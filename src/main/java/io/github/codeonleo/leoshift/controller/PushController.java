package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.config.PushProperties;
import io.github.codeonleo.leoshift.dto.PushSubscriptionRequest;
import io.github.codeonleo.leoshift.service.PushNotificationService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
@Slf4j
public class PushController {

    private final PushNotificationService pushNotificationService;
    private final PushProperties pushProperties;

    public PushController(PushNotificationService pushNotificationService, PushProperties pushProperties) {
        this.pushNotificationService = pushNotificationService;
        this.pushProperties = pushProperties;
    }

    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", pushProperties.publicKey() == null ? "" : pushProperties.publicKey());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> saveSubscription(@Valid @RequestBody PushSubscriptionRequest request) {
        pushNotificationService.saveSubscription(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-reminder")
    public Map<String, Object> sendTestReminder() {
        int sent = pushNotificationService.sendTestNotification();
        return Map.of("sent", sent, "message", "Test notification sent");
    }

    @PostMapping("/send-scheduled-reminder")
    public Map<String, Object> sendScheduledReminder() {
        try {
            log.info("Scheduled reminder endpoint called");
            int sent = pushNotificationService.sendScheduledReminder();
            log.info("Scheduled reminder sent to {} subscribers", sent);
            return Map.of("sent", sent, "message", "Scheduled reminder processed");
        } catch (Exception e) {
            log.error("Failed to send scheduled reminder", e);
            throw e;
        }
    }
}
