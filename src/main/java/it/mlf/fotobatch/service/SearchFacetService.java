package it.mlf.fotobatch.service;

import it.mlf.fotobatch.model.SearchFacetValue;
import it.mlf.fotobatch.model.SearchFacets;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class SearchFacetService {

    private final LocationLookupService locationLookupService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public SearchFacets loadFacets() {
        log.info("Loading gallery facets");
        List<SearchFacetValue> monthYears = loadMonthYearFacets();
        List<SearchFacetValue> localities = loadLocalityFacets();
        log.debug("Loaded facets monthYears={} localities={}", monthYears.size(), localities.size());
        return new SearchFacets(monthYears, localities);
    }

    private List<SearchFacetValue> loadMonthYearFacets() {
        List<Object[]> rows = entityManager.createQuery("""
                        select p.metadataYear, p.metadataMonth, count(p)
                        from PhotoMetadataEntity p
                        where p.metadataYear is not null
                          and p.metadataMonth is not null
                        group by p.metadataYear, p.metadataMonth
                        """, Object[].class)
                .getResultList();

        Map<String, Long> counters = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String year = row[0] == null ? null : row[0].toString();
            String month = row[1] == null ? null : row[1].toString();
            long count = row[2] == null ? 0L : ((Number) row[2]).longValue();
            if (year == null || year.isBlank() || month == null || month.isBlank()) {
                continue;
            }
            String mm = normalizeMonth(month);
            String key = year + "-" + mm;
            counters.merge(key, count, Long::sum);
        }

        List<SearchFacetValue> facets = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            String key = entry.getKey();
            String label = key.substring(5, 7) + "/" + key.substring(0, 4);
            facets.add(new SearchFacetValue(key, label, entry.getValue()));
        }

        facets.sort(Comparator
                .comparingInt((SearchFacetValue value) -> parseIntSafe(value.key().substring(0, 4)))
                .thenComparingInt(value -> parseIntSafe(value.key().substring(5, 7)))
                .reversed());
        return facets;
    }

    private List<SearchFacetValue> loadLocalityFacets() {
        List<Object[]> rows = entityManager.createQuery("""
                        select p.gpsLatitude, p.gpsLongitude, count(p)
                        from PhotoMetadataEntity p
                        where p.gpsLatitude is not null
                          and p.gpsLongitude is not null
                        group by p.gpsLatitude, p.gpsLongitude
                        """, Object[].class)
                .getResultList();

        Map<String, Long> counters = new LinkedHashMap<>();
        Map<String, String> labelsByNormalized = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String latitude = row[0] == null ? "" : row[0].toString();
            String longitude = row[1] == null ? "" : row[1].toString();
            long count = row[2] == null ? 0L : ((Number) row[2]).longValue();

            locationLookupService.resolveLocality(latitude, longitude)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .ifPresent(locality -> {
                        String normalized = normalizeLocality(locality);
                        if (normalized.isEmpty()) {
                            return;
                        }
                        String displayLabel = toDisplayLocality(locality);
                        labelsByNormalized.putIfAbsent(normalized, displayLabel);
                        counters.merge(normalized, count, Long::sum);
                    });
        }

        return counters.entrySet().stream()
                .map(entry -> {
                    String label = labelsByNormalized.getOrDefault(entry.getKey(), entry.getKey());
                    return new SearchFacetValue(label, label, entry.getValue());
                })
                .sorted(Comparator.comparing(SearchFacetValue::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String normalizeLocality(String value) {
        String compact = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return compact.toLowerCase(Locale.ROOT);
    }

    private String toDisplayLocality(String value) {
        String compact = value.trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return compact;
        }

        String[] words = compact.split(" ");
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                label.append(' ');
            }
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return label.toString();
    }

    private String normalizeMonth(String month) {
        String trimmed = month.trim();
        if (trimmed.length() == 1) {
            return "0" + trimmed;
        }
        if (trimmed.length() > 2) {
            return trimmed.substring(trimmed.length() - 2);
        }
        return trimmed;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
