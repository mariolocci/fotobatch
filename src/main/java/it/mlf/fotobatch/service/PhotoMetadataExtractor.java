package it.mlf.fotobatch.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

@Component
@Log4j2
public class PhotoMetadataExtractor {

    public Map<String, String> extract(Path imagePath) throws Exception {
        log.debug("Extracting metadata from {}", imagePath);
        Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
        Map<String, String> result = new TreeMap<>();

        for (Directory directory : metadata.getDirectories()) {
            String directoryName = directory.getName();
            for (Tag tag : directory.getTags()) {
                String key = directoryName + "." + tag.getTagName();
                result.put(key, tag.getDescription());
            }

            if (directory.hasErrors()) {
                directory.getErrors().forEach(error -> result.put(directoryName + ".error", error));
            }
        }

        log.debug("Extracted {} metadata fields from {}", result.size(), imagePath);
        return result;
    }
}
