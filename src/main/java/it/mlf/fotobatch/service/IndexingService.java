package it.mlf.fotobatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.mapper.HashUtils;
import it.mlf.fotobatch.mapper.MetadataFieldMapper;
import it.mlf.fotobatch.model.IndexingReport;
import it.mlf.fotobatch.model.MetadataFieldValues;
import it.mlf.fotobatch.repository.PhotoMetadataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Log4j2
public class IndexingService {

    private static final int BATCH_SIZE = 100;

    private final PathListReader pathListReader;
    private final PhotoFileScanner fileScanner;
    private final PhotoMetadataExtractor metadataExtractor;
    private final LocationLookupService locationLookupService;
    private final ObjectMapper objectMapper;
    private final PhotoMetadataRepository photoMetadataRepository;
 

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public IndexingReport indexFromPathFile(Path inputFile) throws IOException {
        log.info("Indexing from path file {}", inputFile);
        List<Path> roots = pathListReader.readRoots(inputFile);
        log.info("Loaded {} roots from {}", roots.size(), inputFile);
        return indexRoots(roots);
    }

    @Transactional
    public IndexingReport indexFromResourceFile(String resourcePath) throws IOException {
        log.info("Indexing from resource file {}", resourcePath);
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("Indexing resource not found: {}", resourcePath);
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            List<Path> roots = pathListReader.readRoots(inputStream);
            log.info("Loaded {} roots from resource {}", roots.size(), resourcePath);
            return indexRoots(roots);
        }
    }

    private IndexingReport indexRoots(List<Path> roots) throws IOException {
        log.info("Starting indexing for {} roots", roots.size());
        IndexingReport report = new IndexingReport();

        int sinceLastFlush = 0;

        for (Path root : roots) {
            if (!Files.exists(root)) {
                report.incrementMissingRoots();
                continue;
            }

            if (Files.isRegularFile(root)) {
                sinceLastFlush += processSingleFile(root, report);
                if (sinceLastFlush >= BATCH_SIZE) {
                    entityManager.flush();
                    entityManager.clear();
                    sinceLastFlush = 0;
                }
                continue;
            }

            try (Stream<Path> files = fileScanner.scan(root)) {
                for (Path file : files.toList()) {
                    sinceLastFlush += processSingleFile(file, report);
                    if (sinceLastFlush >= BATCH_SIZE) {
                        entityManager.flush();
                        entityManager.clear();
                        sinceLastFlush = 0;
                    }
                }
            }
        }

        log.info("Indexing finished: indexed={} failed={} missingRoots={}",
                report.getIndexedFiles(),
                report.getFailedFiles(),
                report.getMissingRoots());
        return report;
    }

    private int processSingleFile(Path file, IndexingReport report) {
        try {
            Map<String, String> metadata = metadataExtractor.extract(file);
            MetadataFieldValues fields = MetadataFieldMapper.from(metadata);
            String metadataJson = toJson(metadata);
            String metadataHash = HashUtils.sha256Hex(metadataJson);
            String metadataText = toIndexText(metadata);
            String extension = fileScanner.extensionOf(file);
            String absolutePath = file.toAbsolutePath().toString();
            String location = resolveLocation(metadata, fields);

            logDuplicatesByHash(absolutePath, metadataHash);
            upsert(absolutePath, metadataHash, extension, metadataText, metadataJson, fields, location);
            report.incrementIndexedFiles();
            return 1;
        } catch (Exception e) {
            log.warn("Failed indexing file {}: {}", file, e.getMessage());
            report.incrementFailedFiles();
            return 0;
        }
    }

    private void upsert(String absolutePath,
                        String metadataHash,
                        String extension,
                        String metadataText,
                        String metadataJson,
                        MetadataFieldValues fields,
                        String location) {
        PhotoMetadataEntity existing = photoMetadataRepository.findByFilePath(absolutePath).orElse(null);

        if (existing == null) {
            log.debug("Creating new metadata entry for {}", absolutePath);
            photoMetadataRepository.save(new PhotoMetadataEntity(absolutePath, metadataHash, extension, metadataText, metadataJson, fields, location));
            return;
        }

        log.debug("Updating metadata entry for {}", absolutePath);
        existing.update(metadataHash, extension, metadataText, metadataJson, fields, location);
    }

    private String resolveLocation(Map<String, String> metadata, MetadataFieldValues fields) {
        String metadataLocation = extractLocationFromMetadata(metadata);
        if (metadataLocation != null) {
            return metadataLocation;
        }
        return locationLookupService
                .resolveLocality(fields.gpsLatitude(), fields.gpsLongitude())
                .map(this::normalizeLocation)
                .orElse(null);
    }

    private String extractLocationFromMetadata(Map<String, String> metadata) {
        String[] preferredKeyPatterns = {
                "city",
                "sublocation",
                "location",
                "country",
                "state",
                "province",
                "county",
                "municipality"
        };

        for (String pattern : preferredKeyPatterns) {
            String candidate = metadata.entrySet().stream()
                    .filter(entry -> hasText(entry.getValue()))
                    .filter(entry -> normalize(entry.getKey()).contains(pattern))
                    .map(Map.Entry::getValue)
                    .filter(Objects::nonNull)
                    .map(this::normalizeLocation)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String normalizeLocation(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private void logDuplicatesByHash(String absolutePath, String metadataHash) {
        List<String> duplicates = photoMetadataRepository.findAllByMetadataHash(metadataHash).stream()
                .map(PhotoMetadataEntity::getFilePath)
                .filter(path -> !path.equals(absolutePath))
                .sorted()
                .toList();

        if (!duplicates.isEmpty()) {
            log.info("Duplicate hash detected for {} (hash={}): {}", absolutePath, metadataHash, duplicates);
        }
    }

    private String toJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize metadata", e);
        }
    }

    private String toIndexText(Map<String, String> metadata) {
        StringBuilder builder = new StringBuilder();
        metadata.forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        return builder.toString();
    }
}
