package io.github.codeonleo.leoshift.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IcsFeedParser {

    public List<ParsedEvent> parse(String body) {
        List<String> lines = unfold(body);
        List<ParsedEvent> events = new ArrayList<>();
        Map<String, String> properties = null;

        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
                properties = new LinkedHashMap<>();
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(line)) {
                if (properties != null) {
                    events.addAll(toEvents(properties));
                }
                properties = null;
                continue;
            }
            if (properties == null) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            String propertyName = key.split(";", 2)[0].toUpperCase(Locale.ROOT);
            properties.putIfAbsent(propertyName, unescape(value));
        }
        return events;
    }

    private List<ParsedEvent> toEvents(Map<String, String> properties) {
        LocalDate start = parseDate(properties.get("DTSTART"));
        if (start == null) {
            return List.of();
        }
        LocalDate end = parseDate(properties.get("DTEND"));
        if (end == null || end.isBefore(start)) {
            end = start;
        } else if (isDateOnly(properties.get("DTEND")) && end.isAfter(start)) {
            end = end.minusDays(1);
        }

        String title = trimToLimit(properties.getOrDefault("SUMMARY", "외부 일정"), 500);
        String uid = trimToLimit(properties.getOrDefault("UID", title + "-" + start), 512);
        String location = trimToLimit(properties.get("LOCATION"), 500);
        String description = properties.get("DESCRIPTION");
        boolean allDay = isDateOnly(properties.get("DTSTART"));
        String rrule = properties.get("RRULE");

        ParsedEvent base = new ParsedEvent(uid, title, start, end, allDay, location, description);
        if (rrule == null || !rrule.toUpperCase(Locale.ROOT).contains("FREQ=YEARLY")) {
            return List.of(base);
        }

        int currentYear = Year.now().getValue();
        List<ParsedEvent> expanded = new ArrayList<>();
        for (int year = currentYear - 1; year <= currentYear + 3; year++) {
            LocalDate expandedStart = withYearOrSkip(start, year);
            LocalDate expandedEnd = withYearOrSkip(end, year);
            if (expandedStart == null || expandedEnd == null) {
                continue;
            }
            expanded.add(new ParsedEvent(uid + "#" + year, title, expandedStart, expandedEnd, allDay, location, description));
        }
        return expanded;
    }

    private LocalDate withYearOrSkip(LocalDate date, int year) {
        try {
            return date.withYear(year);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> unfold(String body) {
        List<String> lines = new ArrayList<>();
        if (body == null) {
            return lines;
        }
        for (String rawLine : body.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + rawLine.substring(1));
            } else {
                lines.add(rawLine.trim());
            }
        }
        return lines;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 8 && normalized.chars().allMatch(Character::isDigit)) {
                return LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE);
            }
            if (normalized.endsWith("Z")) {
                return OffsetDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")).toLocalDate();
            }
            if (normalized.length() >= 15) {
                return LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(normalized);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isDateOnly(String value) {
        return value != null && value.length() == 8 && value.chars().allMatch(Character::isDigit);
    }

    private String unescape(String value) {
        return value == null ? null : value
                .replace("\\n", "\n")
                .replace("\\N", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private String trimToLimit(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    public record ParsedEvent(
            String uid,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            boolean allDay,
            String location,
            String description
    ) {
    }
}
