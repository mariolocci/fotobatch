package it.mlf.fotobatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import it.mlf.fotobatch.model.MetadataFieldValues;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Table(name = "photo_metadata")
@Indexed
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @KeywordField(projectable = Projectable.YES)
    @Column(nullable = false, unique = true, length = 1024)
    private String filePath;

    @GenericField(projectable = Projectable.YES)
    @Column(nullable = false, length = 64)
    private String metadataHash;

    @KeywordField(projectable = Projectable.YES)
    @Column(nullable = false, length = 32)
    private String extension;

    @FullTextField
    @Column(nullable = false, columnDefinition = "CLOB")
    private String metadataText;

    @FullTextField
    @Column(length = 1024)
    private String title;

    @FullTextField
    @Column(columnDefinition = "CLOB")
    private String description;

    @FullTextField
    @Column(length = 255)
    private String cameraMake;

    @FullTextField
    @Column(length = 255)
    private String cameraModel;

    @FullTextField
    @Column(length = 255)
    private String lensModel;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 128)
    private String dateTimeOriginal;

    @KeywordField(projectable = Projectable.YES)
    @Column(name = "metadata_month", length = 2)
    private String metadataMonth;

    @KeywordField(projectable = Projectable.YES)
    @Column(name = "metadata_year", length = 4)
    private String metadataYear;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String iso;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String aperture;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String shutterSpeed;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String focalLength;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String gpsLatitude;

    @KeywordField(projectable = Projectable.YES)
    @Column(length = 64)
    private String gpsLongitude;

    @FullTextField
    @Column(columnDefinition = "CLOB")
    private String keywords;

    @FullTextField
    @Column(length = 255)
    private String artist;

    @FullTextField
    @Column(length = 512)
    private String copyrightText;

    @FullTextField
    @Column(length = 255)
    private String software;

    @FullTextField
    @Column(length = 255)
    private String location;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String metadataJson;

    public PhotoMetadataEntity(String filePath,
                               String metadataHash,
                               String extension,
                               String metadataText,
                               String metadataJson,
                               MetadataFieldValues fields) {
        this(filePath, metadataHash, extension, metadataText, metadataJson, fields, null);
    }

    public PhotoMetadataEntity(String filePath,
                               String metadataHash,
                               String extension,
                               String metadataText,
                               String metadataJson,
                               MetadataFieldValues fields,
                               String location) {
        this.filePath = filePath;
        this.metadataHash = metadataHash;
        this.extension = extension;
        this.metadataText = metadataText;
        this.metadataJson = metadataJson;
        this.location = location;
        applyFields(fields);
    }

    public void update(String metadataHash,
                       String extension,
                       String metadataText,
                       String metadataJson,
                       MetadataFieldValues fields) {
        update(metadataHash, extension, metadataText, metadataJson, fields, this.location);
    }

    public void update(String metadataHash,
                       String extension,
                       String metadataText,
                       String metadataJson,
                       MetadataFieldValues fields,
                       String location) {
        this.metadataHash = metadataHash;
        this.extension = extension;
        this.metadataText = metadataText;
        this.metadataJson = metadataJson;
        this.location = location;
        applyFields(fields);
    }

    private void applyFields(MetadataFieldValues fields) {
        this.title = fields.title();
        this.description = fields.description();
        this.cameraMake = fields.cameraMake();
        this.cameraModel = fields.cameraModel();
        this.lensModel = fields.lensModel();
        this.dateTimeOriginal = fields.dateTimeOriginal();
        this.metadataMonth = fields.metadataMonth();
        this.metadataYear = fields.metadataYear();
        this.iso = fields.iso();
        this.aperture = fields.aperture();
        this.shutterSpeed = fields.shutterSpeed();
        this.focalLength = fields.focalLength();
        this.gpsLatitude = fields.gpsLatitude();
        this.gpsLongitude = fields.gpsLongitude();
        this.keywords = fields.keywords();
        this.artist = fields.artist();
        this.copyrightText = fields.copyrightText();
        this.software = fields.software();
    }
}