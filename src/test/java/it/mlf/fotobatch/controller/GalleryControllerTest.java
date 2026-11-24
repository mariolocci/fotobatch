package it.mlf.fotobatch.controller;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.MetadataFieldValues;
import it.mlf.fotobatch.model.SearchFacetValue;
import it.mlf.fotobatch.model.SearchFacets;
import it.mlf.fotobatch.model.PhotoSearchItem;
import it.mlf.fotobatch.service.PhotoPresentationService;
import it.mlf.fotobatch.service.SearchFacetService;
import it.mlf.fotobatch.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GalleryController.class)
class GalleryControllerTest {

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SearchFacetService searchFacetService;

    @MockBean
    private PhotoPresentationService photoPresentationService;

    @Test
    void shouldReturnPagedResults() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        PhotoSearchItem item = new PhotoSearchItem(
                "/photos/a.jpg",
                "Rome - a.jpg",
                "2024:05:12 10:11:12",
                "Nikon D750",
                "6016x4016",
                "jpg",
                "4.2 MB",
                "{\"cameraModel\":\"D750\"}",
                List.of()
        );

        when(searchService.searchByMetadata("nikon", 3)).thenReturn(List.of(entity, entity, entity));
        when(photoPresentationService.toSearchItem(entity)).thenReturn(item);

        mockMvc.perform(get("/api/gallery/search")
                        .queryParam("q", "nikon")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].title").value("Rome - a.jpg"))
                .andExpect(jsonPath("$.items[0].photoUrl").doesNotExist())
                .andExpect(jsonPath("$.items[0].thumbnailUrl").doesNotExist());

        verify(searchService).searchByMetadata(eq("nikon"), eq(3));
    }

    @Test
    void shouldReturnFacets() throws Exception {
        SearchFacets facets = new SearchFacets(
                List.of(new SearchFacetValue("2024-05", "05/2024", 10)),
                List.of(new SearchFacetValue("Rome", "Rome", 7))
        );
        when(searchFacetService.loadFacets()).thenReturn(facets);

        mockMvc.perform(get("/api/gallery/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthYears[0].key").value("2024-05"))
                .andExpect(jsonPath("$.localities[0].label").value("Rome"));
    }

    @Test
    void shouldSearchByMonthAndYearFilter() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        PhotoSearchItem item = new PhotoSearchItem(
                "/photos/a.jpg",
                "Rome - a.jpg",
                "2024:05:12 10:11:12",
                "Nikon D750",
                "6016x4016",
                "jpg",
                "4.2 MB",
                "{\"cameraModel\":\"D750\"}",
                List.of()
        );
        when(searchService.searchByMonthAndYear(5, 2024, 3)).thenReturn(List.of(entity, entity, entity));
        when(photoPresentationService.toSearchItem(entity)).thenReturn(item);

        mockMvc.perform(get("/api/gallery/search")
                        .queryParam("month", "5")
                        .queryParam("year", "2024")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        verify(searchService).searchByMonthAndYear(eq(5), eq(2024), eq(3));
    }

    @Test
    void shouldSearchByLocalityFilter() throws Exception {
        PhotoMetadataEntity entity = sampleEntity();
        PhotoSearchItem item = new PhotoSearchItem(
                "/photos/a.jpg",
                "Rome - a.jpg",
                "2024:05:12 10:11:12",
                "Nikon D750",
                "6016x4016",
                "jpg",
                "4.2 MB",
                "{\"cameraModel\":\"D750\"}",
                List.of()
        );
        when(searchService.searchByLocality("Rome", 3)).thenReturn(List.of(entity, entity, entity));
        when(photoPresentationService.toSearchItem(entity)).thenReturn(item);

        mockMvc.perform(get("/api/gallery/search")
                        .queryParam("locality", "Rome")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        verify(searchService).searchByLocality(eq("Rome"), eq(3));
    }

    @Test
    void shouldReturnThumbnailImage() throws Exception {
        Path imagePath = tempDir.resolve("sample.jpg");
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", imagePath.toFile());

        mockMvc.perform(get("/api/gallery/thumbnail")
                        .queryParam("path", imagePath.toString())
                        .queryParam("w", "120")
                        .queryParam("h", "90"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void shouldReturnOriginalPhoto() throws Exception {
        Path imagePath = tempDir.resolve("sample-full.jpg");
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", imagePath.toFile());

        mockMvc.perform(get("/api/gallery/photo")
                        .queryParam("path", imagePath.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void shouldConvertDngToJpegForBrowser() throws Exception {
        Path dngPath = tempDir.resolve("sample.dng");
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", dngPath.toFile());

        mockMvc.perform(get("/api/gallery/photo")
                        .queryParam("path", dngPath.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void shouldPreferSiblingJpegWhenOpeningDng() throws Exception {
        Path dngPath = tempDir.resolve("sample.dng");
        Files.write(dngPath, new byte[]{1, 2, 3, 4});

        Path jpegPath = tempDir.resolve("sample.jpg");
        BufferedImage jpegImage = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(jpegImage, "jpg", jpegPath.toFile());

        mockMvc.perform(get("/api/gallery/photo")
                        .queryParam("path", dngPath.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void shouldReturnUnsupportedMediaTypeWhenDngCannotBeConverted() throws Exception {
        Path dngPath = tempDir.resolve("broken.dng");
        Files.write(dngPath, new byte[]{1, 2, 3, 4});

        mockMvc.perform(get("/api/gallery/photo")
                        .queryParam("path", dngPath.toString()))
                .andExpect(status().isUnsupportedMediaType());
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
                "41.9000",
                "12.5000",
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
