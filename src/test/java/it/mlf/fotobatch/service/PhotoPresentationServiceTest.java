package it.mlf.fotobatch.service;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.MetadataFieldValues;
import it.mlf.fotobatch.model.PhotoSearchItem;
import it.mlf.fotobatch.repository.PhotoMetadataRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoPresentationServiceTest {

    @Test
    void shouldBuildTitleUsingLocalityWhenGpsExists() {
        LocationLookupService locationLookupService = mock(LocationLookupService.class);
        PhotoMetadataRepository photoMetadataRepository = mock(PhotoMetadataRepository.class);
        when(locationLookupService.resolveLocality(eq("41.9028"), eq("12.4964")))
                .thenReturn(Optional.of("Roma"));
        when(photoMetadataRepository.findAllByMetadataHash(eq("hash")))
                .thenReturn(List.of(
                        sampleEntity("/photos/trip/IMG_0001.jpg", "41.9028", "12.4964"),
                        sampleEntity("/photos/trip/IMG_0001_copy.jpg", "41.9028", "12.4964")
                ));

        PhotoPresentationService service = new PhotoPresentationService(locationLookupService, photoMetadataRepository);
        PhotoMetadataEntity entity = sampleEntity("/photos/trip/IMG_0001.jpg", "41.9028", "12.4964");

        PhotoSearchItem result = service.toSearchItem(entity);

        assertEquals("Roma - IMG_0001.jpg", result.title());
        assertTrue(result.duplicatePaths().contains("/photos/trip/IMG_0001_copy.jpg"));
        assertEquals(entity.getMetadataJson(), result.description());
    }

    @Test
    void shouldFallbackToFileNameWhenLocalityMissing() {
        LocationLookupService locationLookupService = mock(LocationLookupService.class);
        PhotoMetadataRepository photoMetadataRepository = mock(PhotoMetadataRepository.class);
        when(locationLookupService.resolveLocality(eq("41.9028"), eq("12.4964")))
                .thenReturn(Optional.empty());
        when(photoMetadataRepository.findAllByMetadataHash(eq("hash")))
                .thenReturn(List.of(sampleEntity("/photos/trip/IMG_0002.jpg", "41.9028", "12.4964")));

        PhotoPresentationService service = new PhotoPresentationService(locationLookupService, photoMetadataRepository);
        PhotoMetadataEntity entity = sampleEntity("/photos/trip/IMG_0002.jpg", "41.9028", "12.4964");

        PhotoSearchItem result = service.toSearchItem(entity);

        assertEquals("IMG_0002.jpg", result.title());
        assertTrue(result.duplicatePaths().isEmpty());
    }

    private PhotoMetadataEntity sampleEntity(String filePath, String lat, String lon) {
        MetadataFieldValues fields = new MetadataFieldValues(
                "Title",
                "Description",
                "Nikon",
                "D750",
                "24-70",
                "2024:05:12 10:11:12",
                "05",
                "2024",
                "200",
                "f/4",
                "1/250",
                "35mm",
                lat,
                lon,
                "travel",
                "Mario",
                null,
                "Lightroom"
        );
        return new PhotoMetadataEntity(
                filePath,
                "hash",
                "jpg",
                "metadata text",
                "{\"cameraModel\":\"D750\"}",
                fields
        );
    }
}
