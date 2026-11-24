package it.mlf.fotobatch.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class PhotoFileScanner {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "dng", "png", "gif", "webp", "tif", "tiff", "bmp", "heic", "heif"
    );

    public Stream<Path> scan(Path root) throws IOException {
        log.debug("Scanning image files under {}", root);
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && isSupportedImage(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException ex) {
                log.warn("Skipping unreadable path {}: {}", file, ex.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException ex) {
                if (ex != null) {
                    log.warn("Skipping unreadable directory {}: {}", dir, ex.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files.stream();
    }

    public String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "unknown";
        }
        return name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedImage(Path file) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(file));
    }
}
