package it.mlf.fotobatch.mapper;

import it.mlf.fotobatch.model.MetadataFieldValues;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MetadataFieldMapper {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(\\d{4})[:/-](\\d{2})");
    private static final Pattern MONTH_YEAR_PATTERN = Pattern.compile("(\\d{2})[:/-](\\d{4})");

    private MetadataFieldMapper() {
    }

    public static MetadataFieldValues from(Map<String, String> metadata) {
        String dateTimeOriginal = firstByKey(metadata, "datetimeoriginal", "datecreated", "createdate");
        String[] monthAndYear = extractMonthAndYear(dateTimeOriginal);

        return new MetadataFieldValues(
                firstByKey(metadata, "title", "dc:title", "objectname"),
                firstByKey(metadata, "description", "imagedescription", "caption", "dc:description"),
                firstByKey(metadata, "make", "cameramake"),
                firstByKey(metadata, "model", "cameramodel"),
                firstByKey(metadata, "lensmodel", "lens"),
                dateTimeOriginal,
                monthAndYear[0],
                monthAndYear[1],
                firstByKey(metadata, "isospeedratings", "iso"),
                firstByKey(metadata, "fnumber", "aperture"),
                firstByKey(metadata, "exposuretime", "shutterspeedvalue", "shutterspeed"),
                firstByKey(metadata, "focallength"),
                firstByKey(metadata, "gpslatitude"),
                firstByKey(metadata, "gpslongitude"),
                firstByKey(metadata, "keywords", "subject"),
                firstByKey(metadata, "artist", "creator"),
                firstByKey(metadata, "copyright"),
                firstByKey(metadata, "software", "processingsoftware")
        );
    }

    private static String firstByKey(Map<String, String> metadata, String... patterns) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }

            String normalizedKey = normalize(entry.getKey());
            for (String pattern : patterns) {
                if (normalizedKey.contains(normalize(pattern))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String normalize(String value) {
        String lower = Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^a-z0-9]", "");
    }

    private static String[] extractMonthAndYear(String dateTimeOriginal) {
        if (dateTimeOriginal == null || dateTimeOriginal.isBlank()) {
            return new String[]{null, null};
        }

        Matcher yearMonth = YEAR_MONTH_PATTERN.matcher(dateTimeOriginal);
        if (yearMonth.find()) {
            String month = yearMonth.group(2);
            if (isValidMonth(month)) {
                return new String[]{month, yearMonth.group(1)};
            }
        }

        Matcher monthYear = MONTH_YEAR_PATTERN.matcher(dateTimeOriginal);
        if (monthYear.find()) {
            String month = monthYear.group(1);
            if (isValidMonth(month)) {
                return new String[]{month, monthYear.group(2)};
            }
        }

        return new String[]{null, null};
    }

    private static boolean isValidMonth(String month) {
        int value = Integer.parseInt(month);
        return value >= 1 && value <= 12;
    }
}
