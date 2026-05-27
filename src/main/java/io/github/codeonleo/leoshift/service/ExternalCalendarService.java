package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.ExternalCalendarEventDto;
import io.github.codeonleo.leoshift.dto.ExternalCalendarSourceRequest;
import io.github.codeonleo.leoshift.dto.ExternalCalendarSourceResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ExternalCalendarEvent;
import io.github.codeonleo.leoshift.entity.ExternalCalendarSource;
import io.github.codeonleo.leoshift.repository.ExternalCalendarEventRepository;
import io.github.codeonleo.leoshift.repository.ExternalCalendarSourceRepository;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExternalCalendarService {

    private static final String DEFAULT_COLOR = "#5E5CE6";

    private final ExternalCalendarSourceRepository sourceRepository;
    private final ExternalCalendarEventRepository eventRepository;
    private final IcsFeedParser parser;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<ExternalCalendarSourceResponse> listSources(Calendar calendar) {
        return sourceRepository.findByCalendarOrderByNameAsc(calendar).stream()
                .map(this::toSourceResponse)
                .toList();
    }

    @Transactional
    public ExternalCalendarSourceResponse createSource(Calendar calendar, ExternalCalendarSourceRequest request) {
        ExternalCalendarSource source = new ExternalCalendarSource();
        source.setCalendar(calendar);
        source.setName(request.name().trim());
        source.setFeedUrl(validateFeedUrl(request.feedUrl()));
        source.setColor(normalizeColor(request.color()));
        source.setActive(true);
        source = sourceRepository.save(source);
        syncSource(source);
        return toSourceResponse(source);
    }

    @Transactional
    public ExternalCalendarSourceResponse syncSource(Calendar calendar, Long sourceId) {
        ExternalCalendarSource source = findSource(calendar, sourceId);
        syncSource(source);
        return toSourceResponse(source);
    }

    @Transactional
    public void deleteSource(Calendar calendar, Long sourceId) {
        ExternalCalendarSource source = findSource(calendar, sourceId);
        eventRepository.deleteBySource(source);
        sourceRepository.delete(source);
    }

    public List<ExternalCalendarEventDto> getEventsInRange(Calendar calendar, LocalDate start, LocalDate end) {
        return eventRepository.findVisibleEvents(calendar, start, end).stream()
                .map(this::toEventDto)
                .toList();
    }

    public void deleteByCalendar(Calendar calendar) {
        sourceRepository.findByCalendarOrderByNameAsc(calendar).forEach(source -> {
            eventRepository.deleteBySource(source);
            sourceRepository.delete(source);
        });
    }

    private void syncSource(ExternalCalendarSource source) {
        try {
            String body = fetch(source.getFeedUrl());
            List<IcsFeedParser.ParsedEvent> parsedEvents = parser.parse(body);
            eventRepository.deleteBySource(source);
            parsedEvents.stream()
                    .map(parsed -> toEntity(source, parsed))
                    .forEach(eventRepository::save);
            source.setLastSyncedAt(LocalDateTime.now());
            source.setLastError(null);
        } catch (RuntimeException e) {
            source.setLastError(e.getMessage());
        } catch (Exception e) {
            source.setLastError(e.getMessage());
        }
    }

    private String fetch(String feedUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "text/calendar, text/plain, */*")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("external_calendar_fetch_failed");
        }
        return response.body();
    }

    private ExternalCalendarEvent toEntity(ExternalCalendarSource source, IcsFeedParser.ParsedEvent parsed) {
        ExternalCalendarEvent event = new ExternalCalendarEvent();
        event.setSource(source);
        event.setUid(parsed.uid());
        event.setTitle(StringUtils.hasText(parsed.title()) ? parsed.title() : "외부 일정");
        event.setStartDate(parsed.startDate());
        event.setEndDate(parsed.endDate());
        event.setAllDay(parsed.allDay());
        event.setLocation(parsed.location());
        event.setDescription(parsed.description());
        return event;
    }

    private ExternalCalendarSource findSource(Calendar calendar, Long sourceId) {
        ExternalCalendarSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("external_calendar_source_not_found"));
        if (!source.getCalendar().getId().equals(calendar.getId())) {
            throw new IllegalArgumentException("external_calendar_source_not_found");
        }
        return source;
    }

    private String validateFeedUrl(String feedUrl) {
        try {
            URI uri = URI.create(feedUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("invalid_external_calendar_url");
            }
            return uri.toString();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid_external_calendar_url");
        }
    }

    private String normalizeColor(String color) {
        if (!StringUtils.hasText(color)) {
            return DEFAULT_COLOR;
        }
        String normalized = color.trim();
        return normalized.matches("^#[0-9A-Fa-f]{6}$") ? normalized : DEFAULT_COLOR;
    }

    private ExternalCalendarSourceResponse toSourceResponse(ExternalCalendarSource source) {
        return new ExternalCalendarSourceResponse(
                source.getId(),
                source.getName(),
                source.getColor(),
                source.isActive(),
                source.getLastSyncedAt(),
                source.getLastError()
        );
    }

    private ExternalCalendarEventDto toEventDto(ExternalCalendarEvent event) {
        ExternalCalendarSource source = event.getSource();
        return new ExternalCalendarEventDto(
                source.getId(),
                source.getName(),
                source.getColor(),
                event.getTitle(),
                event.getStartDate(),
                event.getEndDate(),
                event.isAllDay(),
                event.getLocation()
        );
    }
}
