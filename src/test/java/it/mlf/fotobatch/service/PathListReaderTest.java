package it.mlf.fotobatch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathListReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIgnoreCommentsAndEmptyLines() throws Exception {
        Path listFile = tempDir.resolve("paths.txt");
        Files.writeString(listFile, "\n# comment\n/tmp/one\n  /tmp/two  \n");

        PathListReader reader = new PathListReader();
        List<Path> roots = reader.readRoots(listFile);

        assertEquals(List.of(Path.of("/tmp/one"), Path.of("/tmp/two")), roots);
    }
}
