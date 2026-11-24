package it.mlf.fotobatch.mapper;

import it.mlf.fotobatch.model.MetadataFieldValues;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataFieldMapperTest {

    @Test
    void shouldMapCommonExifAndXmpFields() {
        Map<String, String> metadata = Map.of(
                "Exif IFD0.Make", "NIKON CORPORATION",
                "Exif IFD0.Model", "NIKON D750",
                "Exif SubIFD.ISO Speed Ratings", "400",
                "Exif SubIFD.Date/Time Original", "2025:05:10 12:13:14",
                "XMP.dc:title", "My Shot"
        );

        MetadataFieldValues fields = MetadataFieldMapper.from(metadata);

        assertEquals("My Shot", fields.title());
        assertEquals("NIKON CORPORATION", fields.cameraMake());
        assertEquals("NIKON D750", fields.cameraModel());
        assertEquals("400", fields.iso());
        assertEquals("2025:05:10 12:13:14", fields.dateTimeOriginal());
        assertEquals("05", fields.metadataMonth());
        assertEquals("2025", fields.metadataYear());
    }
}
