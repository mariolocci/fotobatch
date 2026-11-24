package it.mlf.fotobatch.service;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.PhotoSearchItem;
import it.mlf.fotobatch.repository.PhotoMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
public class PhotoPresentationService {

    private final LocationLookupService locationLookupService;
    private final PhotoMetadataRepository photoMetadataRepository;

    public PhotoSearchItem toSearchItem(PhotoMetadataEntity entity) {
        String filePath = entity.getFilePath();
        log.debug("Building PhotoSearchItem for {}", filePath);
        String fileName = extractFileName(filePath);

        String title = resolveEntityLocation(entity)
                .map(locality -> locality + " - " + fileName)
                .orElse(fileName);

        String camera = buildCameraLabel(entity);
        String resolution = resolveResolutionLabel(filePath);
        String fileSize = resolveFileSizeLabel(filePath);
        List<String> duplicatePaths = resolveDuplicatePaths(entity);

        return new PhotoSearchItem(
                filePath,
                title,
                entity.getDateTimeOriginal(),
                camera,
                resolution,
                entity.getExtension(),
                fileSize,
                entity.getMetadataJson(),
                duplicatePaths
        );
    }

    private Optional<String> resolveEntityLocation(PhotoMetadataEntity entity) {
        if (entity.getLocation() != null && !entity.getLocation().isBlank()) {
            return Optional.of(entity.getLocation().trim());
        }
        return locationLookupService.resolveLocality(entity.getGpsLatitude(), entity.getGpsLongitude());
    }

    private List<String> resolveDuplicatePaths(PhotoMetadataEntity entity) {
        String hash = entity.getMetadataHash();
        if (hash == null || hash.isBlank()) {
            return List.of();
        }

        String currentPath = entity.getFilePath();
        return photoMetadataRepository.findAllByMetadataHash(hash).stream()
                .map(PhotoMetadataEntity::getFilePath)
                .filter(path -> !path.equals(currentPath))
                .sorted()
                .toList();
    }

    private String buildCameraLabel(PhotoMetadataEntity entity) {
        String make = emptyToNull(entity.getCameraMake());
        String model = emptyToNull(entity.getCameraModel());
        if (make == null && model == null) {
            return "N/D";
        }
        if (make == null) {
            return model;
        }
        if (model == null) {
            return make;
        }
        return make + " " + model;
    }

    private String resolveResolutionLabel(String filePath) {
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(Path.of(filePath).toFile())) {
            if (inputStream == null) {
                return "N/D";
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                return "N/D";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(inputStream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width + "x" + height;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ignored) {
            log.debug("Unable to resolve resolution for {}", filePath);
            return "N/D";
        }
    }

    private String resolveFileSizeLabel(String filePath) {
        try {
            long size = Files.size(Path.of(filePath));
            if (size < 1024) {
                return size + " B";
            }
            double kb = size / 1024.0;
            if (kb < 1024) {
                return String.format(Locale.ROOT, "%.1f KB", kb);
            }
            double mb = kb / 1024.0;
            if (mb < 1024) {
                return String.format(Locale.ROOT, "%.1f MB", mb);
            }
            return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
        } catch (IOException | RuntimeException ignored) {
            log.debug("Unable to resolve file size for {}", filePath);
            return "N/D";
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String extractFileName(String filePath) {
        try {
            Path path = Path.of(filePath);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
            // Fallback to full path.
        }
        return filePath;
    }
}
