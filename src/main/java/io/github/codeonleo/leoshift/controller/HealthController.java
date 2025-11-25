package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.UserSettingsRepository;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ShiftExceptionRepository shiftExceptionRepository;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/admin/db-stats")
    public Map<String, Object> dbStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalUserSettings", userSettingsRepository.count());
        stats.put("totalShiftExceptions", shiftExceptionRepository.count());
        return stats;
    }
}
