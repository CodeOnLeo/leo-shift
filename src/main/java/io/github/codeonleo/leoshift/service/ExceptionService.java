package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ShiftException;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExceptionService {

    private final ShiftExceptionRepository repository;
    private final CalendarAccessService calendarAccessService;

    @Transactional
    public ShiftException saveOrUpdate(LocalDate date, String customCode, String memo, String anniversaryMemo, boolean repeatYearly, Calendar calendar) {
        ShiftException entity = repository.findByCalendarAndDate(calendar, date).orElseGet(() -> {
            ShiftException ex = new ShiftException();
            ex.setDate(date);
            ex.setCalendar(calendar);
            return ex;
        });
        boolean hasContent = StringUtils.hasText(customCode) || StringUtils.hasText(memo) || StringUtils.hasText(anniversaryMemo) || repeatYearly;
        if (!hasContent) {
            if (entity.getId() != null) {
                repository.delete(entity);
            }
            return null;
        }
        entity.setCustomCode(StringUtils.hasText(customCode) ? customCode.trim().toUpperCase() : null);
        entity.setMemo(StringUtils.hasText(memo) ? memo.trim() : null);
        entity.setAnniversaryMemo(StringUtils.hasText(anniversaryMemo) ? anniversaryMemo.trim() : null);
        entity.setRepeatYearly(repeatYearly);

        // 메모나 기념일 메모가 있을 때만 author 설정, 없으면 null로 초기화
        boolean hasMemoContent = StringUtils.hasText(memo) || StringUtils.hasText(anniversaryMemo);
        if (hasMemoContent) {
            User author = calendarAccessService.getCurrentUser();
            entity.setAuthor(author);
        } else {
            entity.setAuthor(null);
        }

        return repository.save(entity);
    }
}
