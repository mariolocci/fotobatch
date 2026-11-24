package it.mlf.fotobatch.service;

import it.mlf.fotobatch.entity.PhotoMetadataEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.hibernate.search.mapper.orm.Search;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Log4j2
public class SearchService {

    private final LocationLookupService locationLookupService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PhotoMetadataEntity> searchByMetadata(String query, int limit) {
        log.info("Search by metadata query='{}' limit={}", query, limit);
        List<PhotoMetadataEntity> hits = Search.session(entityManager)
                .search(PhotoMetadataEntity.class)
                .where(f -> f.simpleQueryString()
                        .fields(
                                "metadataText",
                                "title",
                                "description",
                                "cameraMake",
                                "cameraModel",
                                "lensModel",
                                "metadataMonth",
                                "metadataYear",
                                "keywords",
                                "artist",
                                "copyrightText",
                                "software",
                                "location"
                        )
                        .matching(query))
                .fetchHits(limit);
        log.debug("Metadata search hits={}", hits.size());
        return hits;
    }

    @Transactional(readOnly = true)
    public List<PhotoMetadataEntity> searchByLuceneSyntax(String luceneQuery, int limit) {
        log.info("Search by Lucene query='{}' limit={}", luceneQuery, limit);
        List<PhotoMetadataEntity> hits = Search.session(entityManager)
                .search(PhotoMetadataEntity.class)
                .where(f -> f.queryString()
                        .fields(
                                "filePath",
                                "metadataHash",
                                "extension",
                                "metadataText",
                                "title",
                                "description",
                                "cameraMake",
                                "cameraModel",
                                "lensModel",
                                "metadataMonth",
                                "metadataYear",
                                "keywords",
                                "artist",
                                "copyrightText",
                                "software",
                                "location"
                        )
                        .matching(luceneQuery))
                .fetchHits(limit);
        log.debug("Lucene search hits={}", hits.size());
        return hits;
    }

    @Transactional(readOnly = true)
    public List<PhotoMetadataEntity> searchByMonthAndYear(int month, int year, int limit) {
        log.info("Search by month/year month={} year={} limit={}", month, year, limit);
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (year < 1) {
            throw new IllegalArgumentException("year must be >= 1");
        }

        String mm = String.format(Locale.ROOT, "%02d", month);
        String yyyy = String.valueOf(year);

        List<PhotoMetadataEntity> hits = Search.session(entityManager)
                .search(PhotoMetadataEntity.class)
                .where(f -> f.bool(b -> {
                    b.must(f.match().field("metadataMonth").matching(mm));
                    b.must(f.match().field("metadataYear").matching(yyyy));
                }))
                .fetchHits(limit);

        if (!hits.isEmpty()) {
            log.debug("Month/year search hits from index={}", hits.size());
            return hits;
        }

        log.debug("Month/year search index returned 0 hits, using JPQL fallback");
        List<PhotoMetadataEntity> fallbackHits = entityManager.createQuery("""
                        select p
                        from PhotoMetadataEntity p
                        where p.dateTimeOriginal is not null
                          and (
                              lower(p.dateTimeOriginal) like lower(:ymColon)
                              or lower(p.dateTimeOriginal) like lower(:ymDash)
                              or lower(p.dateTimeOriginal) like lower(:ymSlash)
                              or lower(p.dateTimeOriginal) like lower(:mySlash)
                              or lower(p.dateTimeOriginal) like lower(:myDash)
                          )
                        """, PhotoMetadataEntity.class)
                .setParameter("ymColon", "%" + yyyy + ":" + mm + "%")
                .setParameter("ymDash", "%" + yyyy + "-" + mm + "%")
                .setParameter("ymSlash", "%" + yyyy + "/" + mm + "%")
                .setParameter("mySlash", "%" + mm + "/" + yyyy + "%")
                .setParameter("myDash", "%" + mm + "-" + yyyy + "%")
                .setMaxResults(limit)
                .getResultList();
        log.debug("Month/year fallback hits={}", fallbackHits.size());
        return fallbackHits;
    }

    @Transactional(readOnly = true)
    public List<PhotoMetadataEntity> searchByLocality(String locality, int limit) {
        log.info("Search by locality locality='{}' limit={}", locality, limit);
        String normalizedTarget = normalizeLocality(locality);
        if (normalizedTarget.isEmpty()) {
            throw new IllegalArgumentException("locality must not be blank");
        }

        List<PhotoMetadataEntity> indexedLocationHits = Search.session(entityManager)
                .search(PhotoMetadataEntity.class)
                .where(f -> f.simpleQueryString()
                        .fields("location")
                        .matching(locality))
                .fetchHits(limit);
        if (!indexedLocationHits.isEmpty()) {
            log.debug("Locality search matched {} photos using indexed location", indexedLocationHits.size());
            return indexedLocationHits;
        }

        List<PhotoMetadataEntity> candidates = entityManager.createQuery("""
                        select p
                        from PhotoMetadataEntity p
                        where p.location is null
                          and p.gpsLatitude is not null
                          and p.gpsLongitude is not null
                        """, PhotoMetadataEntity.class)
                .getResultList();

        List<PhotoMetadataEntity> hits = new ArrayList<>();
        for (PhotoMetadataEntity candidate : candidates) {
            boolean matches = locationLookupService
                    .resolveLocality(candidate.getGpsLatitude(), candidate.getGpsLongitude())
                    .map(this::normalizeLocality)
                    .map(value -> value.equals(normalizedTarget))
                    .orElse(false);
            if (matches) {
                hits.add(candidate);
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        log.debug("Locality search matched {} photos", hits.size());
        return hits;
    }

    private String normalizeLocality(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
