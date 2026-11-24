package it.mlf.fotobatch.repository;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoMetadataRepository extends JpaRepository<PhotoMetadataEntity, Long> {

    Optional<PhotoMetadataEntity> findByFilePath(String filePath);

    List<PhotoMetadataEntity> findAllByMetadataHash(String metadataHash);
}
