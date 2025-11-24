package io.github.codeonleo.leoshift.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.codeonleo.leoshift.config.PushProperties;
import io.github.codeonleo.leoshift.dto.PushSubscriptionRequest;
import io.github.codeonleo.leoshift.entity.PushSubscription;
import io.github.codeonleo.leoshift.repository.PushSubscriptionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final PushSubscriptionRepository repository;
    private final PushProperties pushProperties;
    private final NotificationPreferenceService preferenceService;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    public void saveSubscription(PushSubscriptionRequest request) {
        if (request == null || !StringUtils.hasText(request.endpoint()) || request.keys() == null) {
            throw new IllegalArgumentException("invalid_subscription");
        }
        PushSubscription subscription = repository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        repository.save(subscription);
    }

    public int sendTestNotification() {
        Optional<UpcomingShift> upcoming = findNextShift(LocalDateTime.now());
        if (upcoming.isEmpty()) {
            log.warn("No upcoming shift found, skipping test notification.");
            return 0;
        }
        String payload = buildPayload(upcoming.get(), true);
        return broadcast(payload);
    }

    public int sendScheduledReminder() {
        LocalDateTime now = LocalDateTime.now();
        Optional<UpcomingShift> upcoming = findNextShift(now);

        if (upcoming.isEmpty()) {
            log.debug("No upcoming shift found.");
            return 0;
        }

        UpcomingShift shift = upcoming.get();
        int minutesBefore = preferenceService.fetchMinutes();
        LocalDateTime reminderTime = shift.startTime().minusMinutes(minutesBefore);

        if (now.isAfter(reminderTime) && now.isBefore(shift.startTime())) {
            log.info("Sending scheduled reminder for shift at {}", shift.startTime());
            String payload = buildPayload(shift, false);
            return broadcast(payload);
        } else if (now.isBefore(reminderTime) && reminderTime.isBefore(now.plusHours(1))) {
            log.info("Reminder scheduled for {} (within next hour)", reminderTime);
            String payload = buildPayload(shift, false);
            return broadcast(payload);
        } else {
            log.debug("Next reminder at {} (shift starts at {})", reminderTime, shift.startTime());
            return 0;
        }
    }

    public Optional<UpcomingShift> findNextShift(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        for (int i = 0; i <= 14; i++) {
            LocalDate targetDate = date.plusDays(i);
            Optional<DaySchedule> schedule = scheduleService.resolveDay(targetDate);
            if (schedule.isEmpty()) {
                continue;
            }
            ShiftCodeDefinition definition = ShiftCodeDefinition.fromCode(schedule.get().effectiveCode());
            if (!definition.isWorkingShift()) {
                continue;
            }
            LocalTime start = definition.startTime();
            if (start == null) {
                continue;
            }
            LocalDateTime shiftStart = LocalDateTime.of(targetDate, start);
            if (shiftStart.isBefore(now)) {
                continue;
            }
            return Optional.of(new UpcomingShift(targetDate, schedule.get().effectiveCode(), definition, shiftStart));
        }
        return Optional.empty();
    }

    private String buildPayload(UpcomingShift shift, boolean testMode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", testMode ? "Shift reminder (test)" : "Upcoming shift");
        payload.put("body", String.format("%s shift starts at %s", shift.definition().label(), shift.startTime().toLocalTime()));
        payload.put("code", shift.code());
        payload.put("timeRange", shift.definition().timeRangeLabel());
        payload.put("minutesBefore", preferenceService.fetchMinutes());
        payload.put("at", shift.startTime().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{}";
        }
    }

    private int broadcast(String payload) {
        if (!StringUtils.hasText(pushProperties.publicKey()) || !StringUtils.hasText(pushProperties.privateKey())) {
            log.warn("Missing VAPID keys, push notifications disabled.");
            return 0;
        }
        PushService pushService = buildPushService();
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        List<PushSubscription> subscriptions = repository.findAll();
        int sent = 0;
        for (PushSubscription subscription : subscriptions) {
            try {
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuth(),
                        data
                );
                pushService.send(notification);
                sent++;
            } catch (GeneralSecurityException | IOException e) {
                log.warn("Failed to send push to {}", subscription.getEndpoint(), e);
            }
        }
        return sent;
    }

    private PushService buildPushService() {
        PushService pushService = new PushService();
        pushService.setPublicKey(pushProperties.publicKey());
        pushService.setPrivateKey(pushProperties.privateKey());
        pushService.setSubject(pushProperties.subject());
        if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
        return pushService;
    }

    public record UpcomingShift(LocalDate date, String code, ShiftCodeDefinition definition, LocalDateTime startTime) {
    }
}
