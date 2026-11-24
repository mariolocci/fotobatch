package it.mlf.fotobatch.model;

public record IndexingRunResponse(
        String resourcePath,
        int indexedFiles,
        int failedFiles,
        int missingRoots
) {
}
