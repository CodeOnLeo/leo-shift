package io.github.codeonleo.leoshift.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShiftCalculationService {

    public String determineCode(List<String> pattern, LocalDate patternStart, LocalDate targetDate) {
        if (pattern == null || pattern.isEmpty() || patternStart == null || targetDate == null) {
            return null;
        }
        long diff = ChronoUnit.DAYS.between(patternStart, targetDate);
        int length = pattern.size();
        int index = (int) floorMod(diff, length);
        return pattern.get(index);
    }

    private long floorMod(long dividend, long divisor) {
        long mod = dividend % divisor;
        return mod >= 0 ? mod : mod + divisor;
    }
}
