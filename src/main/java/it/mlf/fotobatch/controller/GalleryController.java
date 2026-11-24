package it.mlf.fotobatch.controller;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.SearchFacets;
import it.mlf.fotobatch.model.PhotoSearchItem;
import it.mlf.fotobatch.model.PhotoSearchPage;
import it.mlf.fotobatch.service.PhotoPresentationService;
import it.mlf.fotobatch.service.SearchFacetService;
import it.mlf.fotobatch.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5174")
@Log4j2
public class GalleryController {

    private static final String DCRAW_EMU_COMMAND = "dcraw_emu";
    private static final Duration DCRAW_EMU_TIMEOUT = Duration.ofSeconds(20);

    private final SearchService searchService;
    private final SearchFacetService searchFacetService;
    private final PhotoPresentationService photoPresentationService;

    @GetMapping("/facets")
    public SearchFacets facets() {
        log.info("GET /api/gallery/facets");
        return searchFacetService.loadFacets();
    }

    @GetMapping("/search")
    public PhotoSearchPage search(@RequestParam(name = "q", required = false) String query,
                                  @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                  @RequestParam(name = "size", defaultValue = "12") @Min(1) @Max(100) int size,
                                  @RequestParam(name = "month", required = false) @Min(1) @Max(12) Integer month,
                                  @RequestParam(name = "year", required = false) @Min(1) Integer year,
                                  @RequestParam(name = "locality", required = false) String locality,
                                  @RequestParam(name = "syntax", defaultValue = "metadata") String syntax) {
        log.info("GET /api/gallery/search page={} size={} syntax={} hasQuery={} month={} year={} locality={}",
                page,
                size,
                syntax,
                query != null && !query.isBlank(),
                month,
                year,
                locality);
        int from = page * size;
        int fetchLimit = from + size + 1;

        List<PhotoMetadataEntity> rawHits;
        if (locality != null && !locality.isBlank()) {
            rawHits = searchService.searchByLocality(locality, fetchLimit);
        } else if (month != null || year != null) {
            if (month == null || year == null) {
                throw new IllegalArgumentException("month and year must be provided together");
            }
            rawHits = searchService.searchByMonthAndYear(month, year, fetchLimit);
        } else if (query != null && !query.isBlank()) {
            rawHits = isLuceneSyntax(syntax)
                    ? searchService.searchByLuceneSyntax(query, fetchLimit)
                    : searchService.searchByMetadata(query, fetchLimit);
        } else {
            return new PhotoSearchPage(List.of(), page, size, false);
        }

        if (from >= rawHits.size()) {
            return new PhotoSearchPage(List.of(), page, size, false);
        }

        int to = Math.min(from + size, rawHits.size());
        boolean hasNext = rawHits.size() > to;

        List<PhotoSearchItem> items = rawHits.subList(from, to).stream()
                .map(photoPresentationService::toSearchItem)
                .toList();

        log.debug("Gallery search produced {} items, hasNext={}", items.size(), hasNext);

        return new PhotoSearchPage(items, page, size, hasNext);
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@RequestParam("path") @NotBlank String path,
                                            @RequestParam(name = "w", defaultValue = "280") @Min(64) @Max(1200) int width,
                                            @RequestParam(name = "h", defaultValue = "180") @Min(64) @Max(1200) int height) throws IOException {
        log.info("GET /api/gallery/thumbnail path={} width={} height={}", path, width, height);
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            log.warn("Thumbnail source not found: {}", path);
            return ResponseEntity.notFound().build();
        }

        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            log.warn("Thumbnail decoding failed for path={}", path);
            return ResponseEntity.notFound().build();
        }

        BufferedImage resized = resize(source, width, height);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(resized, "jpg", output);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic().getHeaderValue())
                .body(output.toByteArray());
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(@RequestParam("path") @NotBlank String path) throws IOException {
        log.info("GET /api/gallery/photo path={}", path);
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            log.warn("Photo source not found: {}", path);
            return ResponseEntity.notFound().build();
        }

        if (isDng(file)) {
            Path siblingJpeg = findSiblingJpeg(file);
            if (siblingJpeg != null) {
                log.debug("Serving sibling JPEG {} for DNG {}", siblingJpeg, file);
                return fileResponse(siblingJpeg);
            }

            byte[] jpegBytes = convertDngToJpeg(file);
            if (jpegBytes == null) {
                log.warn("DNG conversion failed for {}", file);
                return ResponseEntity.status(415).build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic().getHeaderValue())
                    .body(jpegBytes);
        }

        return fileResponse(file);
    }

    private byte[] convertDngToJpeg(Path file) throws IOException {
        log.debug("Converting DNG to JPEG for {}", file);
        BufferedImage image = readDngUsingDcrawEmu(file);
        if (image == null) {
            log.debug("dcraw_emu conversion unavailable/failed for {}, falling back to ImageIO", file);
            image = ImageIO.read(file.toFile());
        }
        if (image == null) {
            log.warn("Unable to decode DNG {} with both dcraw_emu and ImageIO", file);
            return null;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private Path findSiblingJpeg(Path dngFile) {
        String fileName = dngFile.getFileName() != null ? dngFile.getFileName().toString() : "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return null;
        }

        Path parent = dngFile.getParent();
        if (parent == null) {
            return null;
        }

        String baseName = fileName.substring(0, dotIndex);
        String[] extensions = {".jpg", ".JPG", ".jpeg", ".JPEG"};
        for (String extension : extensions) {
            Path candidate = parent.resolve(baseName + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private ResponseEntity<byte[]> fileResponse(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        MediaType mediaType = detectMediaType(file);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic().getHeaderValue())
                .body(bytes);
    }

    private BufferedImage readDngUsingDcrawEmu(Path dngFile) throws IOException {
        Path tempTiff = Files.createTempFile("fotobatch-dng-", ".tiff");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    DCRAW_EMU_COMMAND,
                    "-T",
                    "-Z",
                    tempTiff.toString(),
                    dngFile.toString()
            );
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process;
            try {
                process = processBuilder.start();
            } catch (IOException ex) {
                log.debug("dcraw_emu command not available for {}: {}", dngFile, ex.getMessage());
                return null;
            }
            try {
                boolean finished = process.waitFor(DCRAW_EMU_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    log.warn("dcraw_emu timed out after {}s for {}", DCRAW_EMU_TIMEOUT.toSeconds(), dngFile);
                    process.destroyForcibly();
                    return null;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("dcraw_emu interrupted while processing {}", dngFile);
                return null;
            }

            if (process.exitValue() != 0 || !Files.isRegularFile(tempTiff)) {
                log.debug("dcraw_emu failed for {} with exitCode={}", dngFile, process.exitValue());
                return null;
            }

            return ImageIO.read(tempTiff.toFile());
        } finally {
            Files.deleteIfExists(tempTiff);
        }
    }

    private boolean isDng(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString() : "";
        return name.toLowerCase(Locale.ROOT).endsWith(".dng");
    }

    private boolean isLuceneSyntax(String syntax) {
        return "lucene".equalsIgnoreCase(syntax);
    }

    private MediaType detectMediaType(Path file) {
        try {
            String mimeType = Files.probeContentType(file);
            if (mimeType != null) {
                return MediaType.parseMediaType(mimeType);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Fallback to binary.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.min((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
            int newWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int newHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (targetWidth - newWidth) / 2;
            int y = (targetHeight - newHeight) / 2;

            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, x, y, newWidth, newHeight, null);
            return target;
        } finally {
            graphics.dispose();
        }
    }
}
