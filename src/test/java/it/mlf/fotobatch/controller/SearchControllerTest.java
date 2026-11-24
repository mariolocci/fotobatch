package it.mlf.fotobatch.controller;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.MetadataFieldValues;
import it.mlf.fotobatch.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void shouldExposeMetadataSearchEndpoint() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        when(searchService.searchByMetadata("nikon", 10)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/search/metadata")
                        .queryParam("q", "nikon")
                        .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filePath").value("/photos/a.jpg"))
                .andExpect(jsonPath("$[0].cameraModel").value("D750"));

        verify(searchService).searchByMetadata(eq("nikon"), eq(10));
    }

    @Test
    void shouldExposeLuceneSearchEndpoint() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        when(searchService.searchByLuceneSyntax("cameraModel:D750", 5)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/search/lucene")
                        .queryParam("q", "cameraModel:D750")
                        .queryParam("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filePath").value("/photos/a.jpg"))
                .andExpect(jsonPath("$[0].metadataHash").value("abc123"));

        verify(searchService).searchByLuceneSyntax(eq("cameraModel:D750"), eq(5));
    }

    @Test
    void shouldExposeMonthYearSearchEndpoint() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        when(searchService.searchByMonthAndYear(5, 2024, 20)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/search/month-year")
                        .queryParam("month", "5")
                        .queryParam("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dateTimeOriginal").value("2024:05:12 10:11:12"))
                .andExpect(jsonPath("$[0].metadataMonth").value("05"))
                .andExpect(jsonPath("$[0].metadataYear").value("2024"))
                .andExpect(jsonPath("$[0].extension").value("jpg"));

        verify(searchService).searchByMonthAndYear(eq(5), eq(2024), eq(20));
    }

    @Test
    void shouldReturnBadRequestWhenMetadataQueryMissing() throws Exception {
        mockMvc.perform(get("/api/search/metadata")
                        .queryParam("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"));

        verifyNoInteractions(searchService);
    }

    @Test
    void shouldReturnBadRequestWhenMonthIsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/search/month-year")
                        .queryParam("month", "13")
                        .queryParam("year", "2024"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    private PhotoMetadataEntity sampleEntity() {
        MetadataFieldValues fields = new MetadataFieldValues(
                "Sunset",
                "Golden hour",
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
                null,
                null,
                "travel,sea",
                "Mario",
                null,
                "Lightroom"
        );
        return new PhotoMetadataEntity(
                "/photos/a.jpg",
                "abc123",
                "jpg",
                "Sunset Nikon D750 travel sea",
                "{\"cameraModel\":\"D750\"}",
                fields
        );
    }
}
