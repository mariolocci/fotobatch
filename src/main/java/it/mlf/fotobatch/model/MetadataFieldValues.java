package it.mlf.fotobatch.model;

public record MetadataFieldValues(
        String title,
        String description,
        String cameraMake,
        String cameraModel,
        String lensModel,
        String dateTimeOriginal,
        String metadataMonth,
        String metadataYear,
        String iso,
        String aperture,
        String shutterSpeed,
        String focalLength,
        String gpsLatitude,
        String gpsLongitude,
        String keywords,
        String artist,
        String copyrightText,
        String software
) {
}
