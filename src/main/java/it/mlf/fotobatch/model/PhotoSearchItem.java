package it.mlf.fotobatch.model;

import java.util.List;

public record PhotoSearchItem(
        String filePath,
        String title,
        String shotDate,
        String camera,
        String resolution,
        String format,
        String fileSize,
        String description,
        List<String> duplicatePaths
) {
}
