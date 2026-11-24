package it.mlf.fotobatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import lombok.extern.log4j.Log4j2;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class LocationLookupService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final double KM_PER_DEGREE = 111.32;
    private static final double MIN_LON_FACTOR = 1.0e-6;

    private final RestClient restClient;
    private final double lookupRangeKm;
    private final ConcurrentMap<String, String> localityCache = new ConcurrentHashMap<>();

    public LocationLookupService(@Value("${fotobatch.location.lookup-range-km:4}") double lookupRangeKm) {
        this.lookupRangeKm = lookupRangeKm > 0 ? lookupRangeKm : 4.0;
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "fotobatch/1.0 (+https://localhost)")
                .build();
    }

    public Optional<String> resolveLocality(String latitudeRaw, String longitudeRaw) {
        log.debug("Resolving locality for lat='{}' lon='{}'", latitudeRaw, longitudeRaw);
        Optional<Double> latitude = parseCoordinate(latitudeRaw);
        Optional<Double> longitude = parseCoordinate(longitudeRaw);
        if (latitude.isEmpty() || longitude.isEmpty()) {
            log.debug("Skipping locality resolve due to invalid coordinates");
            return Optional.empty();
        }

        String cacheKey = toRangeCacheKey(latitude.get(), longitude.get());
        String locality = localityCache.computeIfAbsent(cacheKey,
                key -> reverseGeocode(latitude.get(), longitude.get()).orElse(""));

        if (locality.isBlank()) {
            log.debug("No locality found for {}", cacheKey);
        }
        return locality.isBlank() ? Optional.empty() : Optional.of(locality);
    }

    private String toRangeCacheKey(double latitude, double longitude) {
        double latStepDegrees = lookupRangeKm / KM_PER_DEGREE;
        double lonKmPerDegree = KM_PER_DEGREE * Math.cos(Math.toRadians(latitude));
        double lonStepDegrees = lookupRangeKm / Math.max(MIN_LON_FACTOR, Math.abs(lonKmPerDegree));

        long latBucket = Math.round(latitude / latStepDegrees);
        long lonBucket = Math.round(longitude / lonStepDegrees);
        return latBucket + "," + lonBucket;
    }

    private Optional<String> reverseGeocode(double latitude, double longitude) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("nominatim.openstreetmap.org")
                            .path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("addressdetails", "1")
                            .queryParam("zoom", "10")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (payload == null) {
                return Optional.empty();
            }

            Object addressObj = payload.get("address");
            if (!(addressObj instanceof Map<?, ?> address)) {
                return Optional.empty();
            }

            return firstNonBlank(address,
                    "city",
                    "town",
                    "village",
                    "hamlet",
                    "municipality",
                    "county",
                    "state",
                    "country");
        } catch (Exception ex) {
            // Geocoding is best-effort and should never block search results.
            log.debug("Reverse geocoding failed for lat={} lon={}: {}", latitude, longitude, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> firstNonBlank(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private Optional<Double> parseCoordinate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String normalized = raw.trim().replace(',', '.');

        try {
            return Optional.of(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            // Fall through to DMS parsing.
        }

        return parseDms(normalized);
    }

    private Optional<Double> parseDms(String raw) {
        Matcher matcher = NUMBER_PATTERN.matcher(raw);
        double[] values = new double[3];
        int index = 0;
        while (matcher.find() && index < values.length) {
            values[index++] = Double.parseDouble(matcher.group(1).replace(',', '.'));
        }

        if (index == 0) {
            return Optional.empty();
        }

        double degrees = values[0];
        double minutes = index > 1 ? values[1] : 0.0;
        double seconds = index > 2 ? values[2] : 0.0;

        double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);

        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("S") || upper.contains("W") || raw.startsWith("-")) {
            decimal = -decimal;
        }

        return Optional.of(decimal);
    }
}
