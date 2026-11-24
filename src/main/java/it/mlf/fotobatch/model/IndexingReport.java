package it.mlf.fotobatch.model;

import lombok.Getter;

@Getter
public class IndexingReport {

    private int indexedFiles;
    private int failedFiles;
    private int missingRoots;

    public void incrementIndexedFiles() {
        indexedFiles++;
    }

    public void incrementFailedFiles() {
        failedFiles++;
    }

    public void incrementMissingRoots() {
        missingRoots++;
    }

}
