package it.mlf.fotobatch.config;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import it.mlf.fotobatch.model.IndexingReport;
import it.mlf.fotobatch.service.IndexingService;
import it.mlf.fotobatch.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class BatchRunnerConfig {

    @Bean
    @ConditionalOnProperty(name = "fotobatch.cli.enabled", havingValue = "true")
    CommandLineRunner commandLineRunner(IndexingService indexingService,
                                        SearchService searchService,
                                        ApplicationArguments appArgs) {
        return args -> {
            List<String> nonOptionArgs = appArgs.getNonOptionArgs();
            if (nonOptionArgs.isEmpty()) {
                printUsage();
                return;
            }

            Path inputFile = Path.of(nonOptionArgs.get(0));
            IndexingReport report = indexingService.indexFromPathFile(inputFile);

            System.out.println("Indexing completed");
            System.out.println("Indexed files : " + report.getIndexedFiles());
            System.out.println("Failed files  : " + report.getFailedFiles());
            System.out.println("Missing roots : " + report.getMissingRoots());

            if (nonOptionArgs.size() >= 2) {
                String query = nonOptionArgs.get(1);
                List<PhotoMetadataEntity> hits = searchService.searchByMetadata(query, 20);
                printHits(query, hits);
            }
        };
    }

    private static void printHits(String query, List<PhotoMetadataEntity> hits) {
        System.out.println();
        System.out.println("Search query: " + query);
        if (hits.isEmpty()) {
            System.out.println("No results");
            return;
        }

        for (PhotoMetadataEntity hit : hits) {
            System.out.println("- " + hit.getFilePath() + " | hash=" + hit.getMetadataHash());
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar fotobatch-0.0.1-SNAPSHOT.jar <path-list-file> [search-query]");
        System.out.println("The path list file must contain one absolute or relative path per line.");
    }
}
