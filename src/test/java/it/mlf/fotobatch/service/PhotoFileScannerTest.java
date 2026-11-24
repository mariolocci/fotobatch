package it.mlf.fotobatch.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotoFileScannerTest {

    @Test
    void shouldExtractExtensionInLowercase() {
        PhotoFileScanner scanner = new PhotoFileScanner();
        assertEquals("jpeg", scanner.extensionOf(Path.of("Photo.JPEG")));
    }

    @Test
    void shouldReturnUnknownWhenMissingExtension() {
        PhotoFileScanner scanner = new PhotoFileScanner();
        assertEquals("unknown", scanner.extensionOf(Path.of("photo")));
    }

    @Test
    void shouldRecognizeDngExtension() {
        PhotoFileScanner scanner = new PhotoFileScanner();
        assertEquals("dng", scanner.extensionOf(Path.of("raw.DNG")));
    }
}
