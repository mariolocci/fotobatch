package it.mlf.fotobatch.service;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.MetadataFieldValues;
import it.mlf.fotobatch.model.SearchFacetValue;
import it.mlf.fotobatch.model.SearchFacets;
import it.mlf.fotobatch.repository.PhotoMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "fotobatch.cli.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:fotobatch-facets;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.search.backend.directory.type=local-filesystem",
        "spring.jpa.properties.hibernate.search.backend.directory.root=./target/lucene-index-facets"
})
class SearchFacetServiceTest {

    @Autowired
    private SearchFacetService searchFacetService;

    @Autowired
    private PhotoMetadataRepository photoMetadataRepository;

    @MockBean
    private LocationLookupService locationLookupService;

    @BeforeEach
    void setUp() {
        photoMetadataRepository.deleteAll();
    }

    @Test
    void shouldReturnMonthYearFacetsSortedByMostRecent() {
        when(locationLookupService.resolveLocality("", "")).thenReturn(Optional.empty());

        photoMetadataRepository.save(entity("/photos/a.jpg", "2023", "01", null, null));
        photoMetadataRepository.save(entity("/photos/b.jpg", "2024", "05", null, null));
        photoMetadataRepository.save(entity("/photos/c.jpg", "2024", "12", null, null));
        photoMetadataRepository.save(entity("/photos/d.jpg", "2024", "5", null, null));

        SearchFacets facets = searchFacetService.loadFacets();

        List<SearchFacetValue> monthYears = facets.monthYears();
        assertEquals(3, monthYears.size());
        assertEquals("2024-12", monthYears.get(0).key());
        assertEquals(1L, monthYears.get(0).count());
        assertEquals("2024-05", monthYears.get(1).key());
        assertEquals(2L, monthYears.get(1).count());
        assertEquals("2023-01", monthYears.get(2).key());
    }

    @Test
    void shouldAggregateLocalityCountsAndSortAlphabetically() {
        photoMetadataRepository.save(entity("/photos/a.jpg", "2024", "05", "41.9000", "12.5000"));
        photoMetadataRepository.save(entity("/photos/b.jpg", "2024", "05", "41.9000", "12.5000"));
        photoMetadataRepository.save(entity("/photos/c.jpg", "2024", "05", "45.4642", "9.1900"));
        photoMetadataRepository.save(entity("/photos/d.jpg", "2024", "05", "40.8518", "14.2681"));

        when(locationLookupService.resolveLocality("41.9000", "12.5000")).thenReturn(Optional.of("rome"));
        when(locationLookupService.resolveLocality("45.4642", "9.1900")).thenReturn(Optional.of("Milan"));
        when(locationLookupService.resolveLocality("40.8518", "14.2681")).thenReturn(Optional.of(" ROME  "));

        SearchFacets facets = searchFacetService.loadFacets();

        List<SearchFacetValue> localities = facets.localities();
        assertEquals(2, localities.size());
        assertEquals("Milan", localities.get(0).key());
        assertEquals("Milan", localities.get(0).label());
        assertEquals(1L, localities.get(0).count());
        assertEquals("Rome", localities.get(1).key());
        assertEquals("Rome", localities.get(1).label());
        assertEquals(3L, localities.get(1).count());
    }

    private PhotoMetadataEntity entity(String filePath,
                                       String metadataYear,
                                       String metadataMonth,
                                       String gpsLatitude,
                                       String gpsLongitude) {
        MetadataFieldValues fields = new MetadataFieldValues(
                "title",
                "description",
                "cameraMake",
                "cameraModel",
                "lensModel",
                metadataYear + ":" + metadataMonth + ":01 10:00:00",
                metadataMonth,
                metadataYear,
                "100",
                "f/2.8",
                "1/125",
                "35mm",
                gpsLatitude,
                gpsLongitude,
                "keywords",
                "artist",
                null,
                "software"
        );
        return new PhotoMetadataEntity(
                filePath,
                "hash-" + filePath.hashCode(),
                "jpg",
                "metadata text",
                "{}",
                fields
        );
    }
}
