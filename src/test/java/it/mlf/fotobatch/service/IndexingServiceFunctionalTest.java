package it.mlf.fotobatch.service;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.IndexingReport;
import it.mlf.fotobatch.repository.PhotoMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(properties = {
        "fotobatch.cli.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:fotobatch-functional;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.search.backend.directory.type=local-filesystem",
        "spring.jpa.properties.hibernate.search.backend.directory.root=./target/lucene-index-functional"
})
class IndexingServiceFunctionalTest {

    private static final Path REAL_PHOTO_ROOT = Path.of("/home/mariolocci/Pictures/Camera/recup_dir.1");
    private static final Pattern YEAR_MONTH_AT_START = Pattern.compile("^(\\d{4})[:/-](\\d{2}).*");
    private static final Pattern MONTH_YEAR_AT_START = Pattern.compile("^(\\d{2})[:/-](\\d{4}).*");

    @Autowired
    private IndexingService indexingService;

    @Autowired
    private PhotoFileScanner photoFileScanner;

    @Autowired
    private PhotoMetadataRepository photoMetadataRepository;

    @Autowired
    private SearchService searchService;

    @Test
    void shouldIndexAllSupportedPhotosFromRealFolder() throws Exception {
        assumeTrue(Files.isDirectory(REAL_PHOTO_ROOT),
                "Required folder not found: " + REAL_PHOTO_ROOT);

        photoMetadataRepository.deleteAll();

        long supportedPhotoCount;
        try (Stream<Path> files = photoFileScanner.scan(REAL_PHOTO_ROOT)) {
            supportedPhotoCount = files.count();
        }

        Path listFile = Files.createTempFile("fotobatch-functional-paths", ".txt");
        Files.writeString(listFile, REAL_PHOTO_ROOT.toString() + System.lineSeparator());

        try {
            IndexingReport report = indexingService.indexFromPathFile(listFile);

            assertEquals(0, report.getMissingRoots(), "No root should be missing");
            assertEquals(
                    supportedPhotoCount,
                    (long) report.getIndexedFiles() + report.getFailedFiles(),
                    "All supported files must be processed (indexed or failed)"
            );
            assertEquals(report.getIndexedFiles(), photoMetadataRepository.count(),
                    "Indexed records in DB should match report");

            if (report.getIndexedFiles() > 0) {
                List<PhotoMetadataEntity> sample = photoMetadataRepository.findAll(PageRequest.of(0, 1)).getContent();
                assertFalse(sample.isEmpty(), "At least one indexed record is expected");

                PhotoMetadataEntity first = sample.getFirst();
                assertNotNull(first.getFilePath());
                assertNotNull(first.getMetadataHash());
                assertFalse(first.getMetadataHash().isBlank());
                assertNotNull(first.getMetadataJson());
                assertFalse(first.getMetadataJson().isBlank());
                assertTrue(Files.exists(Path.of(first.getFilePath())),
                        "Indexed file path should exist on disk");
            }
        } finally {
            Files.deleteIfExists(listFile);
        }
    }

    @Test
    void shouldFindIndexedPhotoUsingSearchService() throws Exception {
        assumeTrue(Files.isDirectory(REAL_PHOTO_ROOT),
                "Required folder not found: " + REAL_PHOTO_ROOT);

        photoMetadataRepository.deleteAll();

        Path listFile = Files.createTempFile("fotobatch-functional-search", ".txt");
        Files.writeString(listFile, REAL_PHOTO_ROOT.toString() + System.lineSeparator());

        try {
            IndexingReport report = indexingService.indexFromPathFile(listFile);
            assumeTrue(report.getIndexedFiles() > 0, "No photos indexed, cannot verify search");

            List<PhotoMetadataEntity> sample = photoMetadataRepository.findAll(PageRequest.of(0, 1)).getContent();
            assertFalse(sample.isEmpty(), "Expected at least one indexed photo");

            PhotoMetadataEntity first = sample.getFirst();
            String searchTerm = pickSearchTerm(first);
            assumeTrue(searchTerm != null && !searchTerm.isBlank(),
                    "No suitable searchable token found in indexed metadata");

            List<PhotoMetadataEntity> hits = searchService.searchByMetadata(searchTerm, 50);

            assertFalse(hits.isEmpty(), "Search should return at least one hit");
            assertTrue(
                    hits.stream().anyMatch(hit -> hit.getFilePath().equals(first.getFilePath())),
                    "Expected indexed sample photo to be found by metadata search"
            );
        } finally {
            Files.deleteIfExists(listFile);
        }
    }

    @Test
    void shouldFindIndexedPhotoUsingLuceneSyntaxQuery() throws Exception {
        assumeTrue(Files.isDirectory(REAL_PHOTO_ROOT),
                "Required folder not found: " + REAL_PHOTO_ROOT);

        photoMetadataRepository.deleteAll();

        Path listFile = Files.createTempFile("fotobatch-functional-lucene", ".txt");
        Files.writeString(listFile, REAL_PHOTO_ROOT.toString() + System.lineSeparator());

        try {
            IndexingReport report = indexingService.indexFromPathFile(listFile);
            assumeTrue(report.getIndexedFiles() > 0, "No photos indexed, cannot verify Lucene syntax search");

            List<PhotoMetadataEntity> sample = photoMetadataRepository.findAll(PageRequest.of(0, 1)).getContent();
            assertFalse(sample.isEmpty(), "Expected at least one indexed photo");

            PhotoMetadataEntity first = sample.getFirst();
            String luceneQuery = "filePath:\"" + escapeLuceneTerm(first.getFilePath()) + "\"";
            assumeTrue(luceneQuery != null && !luceneQuery.isBlank(),
                    "No suitable field token available to build Lucene query");

            List<PhotoMetadataEntity> hits = searchService.searchByLuceneSyntax(luceneQuery, 50);

            assertFalse(hits.isEmpty(), "Lucene syntax search should return at least one hit");
            assertTrue(
                    hits.stream().anyMatch(hit -> hit.getFilePath().equals(first.getFilePath())),
                    "Expected indexed sample photo to be found by Lucene syntax query: " + luceneQuery
            );
        } finally {
            Files.deleteIfExists(listFile);
        }
    }

    @Test
    void shouldFindIndexedPhotoUsingMonthAndYearSearch() throws Exception {
        assumeTrue(Files.isDirectory(REAL_PHOTO_ROOT),
                "Required folder not found: " + REAL_PHOTO_ROOT);

        photoMetadataRepository.deleteAll();

        Path listFile = Files.createTempFile("fotobatch-functional-month-year", ".txt");
        Files.writeString(listFile, REAL_PHOTO_ROOT.toString() + System.lineSeparator());

        try {
            IndexingReport report = indexingService.indexFromPathFile(listFile);
            assumeTrue(report.getIndexedFiles() > 0, "No photos indexed, cannot verify month/year search");

            List<PhotoMetadataEntity> all = photoMetadataRepository.findAll();
            Optional<PhotoMetadataEntity> sampleWithDate = all.stream()
                    .filter(e -> e.getDateTimeOriginal() != null && !e.getDateTimeOriginal().isBlank())
                    .filter(e -> extractMonthYear(e.getDateTimeOriginal()).isPresent())
                    .findFirst();

            assumeTrue(sampleWithDate.isPresent(),
                    "No indexed photo has a parseable dateTimeOriginal to test month/year search");

            PhotoMetadataEntity sample = sampleWithDate.get();
            int[] monthYear = extractMonthYear(sample.getDateTimeOriginal()).orElseThrow();
            assertEquals(String.format("%02d", monthYear[0]), sample.getMetadataMonth(),
                    "metadataMonth should be derived from dateTimeOriginal");
            assertEquals(String.valueOf(monthYear[1]), sample.getMetadataYear(),
                    "metadataYear should be derived from dateTimeOriginal");

            List<PhotoMetadataEntity> hits = searchService.searchByMonthAndYear(monthYear[0], monthYear[1], 200);

            assertFalse(hits.isEmpty(), "Month/year search should return at least one hit");
            assertTrue(
                    hits.stream().anyMatch(hit -> hit.getFilePath().equals(sample.getFilePath())),
                    "Expected indexed sample photo to be found by month/year search"
            );
        } finally {
            Files.deleteIfExists(listFile);
        }
    }

    private String pickSearchTerm(PhotoMetadataEntity entity) {
        String[] preferred = {
                entity.getCameraModel(),
                entity.getCameraMake(),
                entity.getLensModel(),
                entity.getArtist(),
                entity.getTitle(),
                entity.getKeywords()
        };

        for (String value : preferred) {
            if (value != null && !value.isBlank()) {
                String token = firstUsefulToken(value);
                if (token != null) {
                    return token;
                }
            }
        }

        return firstUsefulToken(entity.getMetadataText());
    }

    private String firstUsefulToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] tokens = value.split("[^A-Za-z0-9]+");
        for (String token : tokens) {
            if (token.length() >= 4) {
                return token;
            }
        }
        return null;
    }

    private String escapeLuceneTerm(String term) {
        return term.replace("\\", "\\\\")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("&&", "\\&&")
                .replace("||", "\\||")
                .replace("!", "\\!")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("^", "\\^")
                .replace("\"", "\\\"")
                .replace("~", "\\~")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace(":", "\\:")
                .replace("/", "\\/");
    }

    private Optional<int[]> extractMonthYear(String dateTimeOriginal) {
        Matcher yearMonth = YEAR_MONTH_AT_START.matcher(dateTimeOriginal);
        if (yearMonth.matches()) {
            int year = Integer.parseInt(yearMonth.group(1));
            int month = Integer.parseInt(yearMonth.group(2));
            if (month >= 1 && month <= 12) {
                return Optional.of(new int[]{month, year});
            }
        }

        Matcher monthYear = MONTH_YEAR_AT_START.matcher(dateTimeOriginal);
        if (monthYear.matches()) {
            int month = Integer.parseInt(monthYear.group(1));
            int year = Integer.parseInt(monthYear.group(2));
            if (month >= 1 && month <= 12) {
                return Optional.of(new int[]{month, year});
            }
        }

        return Optional.empty();
    }
}
