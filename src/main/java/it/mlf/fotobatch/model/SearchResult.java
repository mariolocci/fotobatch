package it.mlf.fotobatch.model;

public record SearchResult(
        String filePath,
        String metadataHash,
        String extension,
        String dateTimeOriginal,
        String metadataMonth,
        String metadataYear,
        String title,
        String cameraMake,
        String cameraModel,
        String lensModel,
        String keywords,
        String artist,
        String metadataJson
) {
}
