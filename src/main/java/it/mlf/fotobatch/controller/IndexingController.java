package it.mlf.fotobatch.controller;

import it.mlf.fotobatch.model.IndexingReport;
import it.mlf.fotobatch.model.IndexingRunResponse;
import it.mlf.fotobatch.service.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/indexing")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5174")
@Log4j2
public class IndexingController {

    private final IndexingService indexingService;

    @Value("${fotobatch.indexing.paths-resource:indexing-paths.txt}")
    private String pathsResource;

    @GetMapping("/config")
    public Map<String, String> config() {
        log.info("GET /api/indexing/config resourcePath={}", pathsResource);
        return Map.of("resourcePath", pathsResource);
    }

    @PostMapping("/run")
    public IndexingRunResponse run() throws IOException {
        log.info("POST /api/indexing/run resourcePath={}", pathsResource);
        IndexingReport report = indexingService.indexFromResourceFile(pathsResource);
        log.info("Indexing completed: indexed={} failed={} missingRoots={}",
                report.getIndexedFiles(),
                report.getFailedFiles(),
                report.getMissingRoots());
        return new IndexingRunResponse(
                pathsResource,
                report.getIndexedFiles(),
                report.getFailedFiles(),
                report.getMissingRoots()
        );
    }
}
