package it.mlf.fotobatch.controller;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.SearchResult;
import it.mlf.fotobatch.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
@Log4j2
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/metadata")
    public List<SearchResult> searchByMetadata(@RequestParam("q") @NotBlank String query,
                                               @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(500) int limit) {
        log.info("GET /api/search/metadata query='{}' limit={}", query, limit);
        List<SearchResult> results = searchService.searchByMetadata(query, limit).stream()
                .map(SearchController::toResult)
                .toList();
        log.debug("Metadata search returned {} results", results.size());
        return results;
    }

    @GetMapping("/lucene")
    public List<SearchResult> searchByLucene(@RequestParam("q") @NotBlank String luceneQuery,
                                             @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(500) int limit) {
        log.info("GET /api/search/lucene query='{}' limit={}", luceneQuery, limit);
        List<SearchResult> results = searchService.searchByLuceneSyntax(luceneQuery, limit).stream()
                .map(SearchController::toResult)
                .toList();
        log.debug("Lucene search returned {} results", results.size());
        return results;
    }

    @GetMapping("/month-year")
    public List<SearchResult> searchByMonthAndYear(@RequestParam("month") @Min(1) @Max(12) int month,
                                                    @RequestParam("year") @Min(1) int year,
                                                    @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(500) int limit) {
        log.info("GET /api/search/month-year month={} year={} limit={}", month, year, limit);
        List<SearchResult> results = searchService.searchByMonthAndYear(month, year, limit).stream()
                .map(SearchController::toResult)
                .toList();
        log.debug("Month/year search returned {} results", results.size());
        return results;
    }

    private static SearchResult toResult(PhotoMetadataEntity entity) {
        return new SearchResult(
                entity.getFilePath(),
                entity.getMetadataHash(),
                entity.getExtension(),
                entity.getDateTimeOriginal(),
                entity.getMetadataMonth(),
                entity.getMetadataYear(),
                entity.getTitle(),
                entity.getCameraMake(),
                entity.getCameraModel(),
                entity.getLensModel(),
                entity.getKeywords(),
                entity.getArtist(),
                entity.getMetadataJson()
        );
    }
}
