package it.mlf.fotobatch.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class PathListReader {

    public List<Path> readRoots(Path inputFile) throws IOException {
        log.debug("Reading roots from file {}", inputFile);
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            return readRoots(reader);
        }
    }

    public List<Path> readRoots(InputStream inputStream) throws IOException {
        log.debug("Reading roots from input stream");
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return readRoots(reader);
        }
    }

    private List<Path> readRoots(Reader reader) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader);
             Stream<String> lines = bufferedReader.lines()) {
            List<Path> roots = lines
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .map(Path::of)
                    .toList();
            log.debug("Loaded {} root paths", roots.size());
            return roots;
        }
    }
}
