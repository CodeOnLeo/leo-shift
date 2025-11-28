package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.AuthorDto;
import io.github.codeonleo.leoshift.dto.MemoDto;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.DayMemo;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.DayMemoRepository;
import io.github.codeonleo.leoshift.util.ColorTagUtil;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DayMemoService {

    private final DayMemoRepository repository;
    private final CalendarAccessService calendarAccessService;

    @Transactional
    public DayMemo saveOrUpdate(LocalDate date, String memoText, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();

        // 현재 사용자가 해당 날짜에 작성한 메모가 있는지 확인
        DayMemo entity = repository.findByCalendarAndDateAndAuthor(calendar, date, currentUser)
                .orElseGet(() -> {
                    DayMemo memo = new DayMemo();
                    memo.setDate(date);
                    memo.setCalendar(calendar);
                    memo.setAuthor(currentUser);
                    memo.setMemoType(DayMemo.MemoType.GENERAL);
                    return memo;
                });

        // 메모 내용이 없으면 삭제
        if (!StringUtils.hasText(memoText)) {
            if (entity.getId() != null) {
                repository.delete(entity);
            }
            return null;
        }

        entity.setMemo(memoText.trim());
        return repository.save(entity);
    }

    @Transactional
    public void deleteById(Long memoId, User user) {
        DayMemo memo = repository.findByIdAndAuthor(memoId, user)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없거나 삭제 권한이 없습니다."));
        repository.delete(memo);
    }

    public List<MemoDto> getMemos(LocalDate date, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        List<DayMemo> memos = repository.findByCalendarAndDate(calendar, date);

        return memos.stream()
                .map(memo -> toDto(memo, currentUser))
                .collect(Collectors.toList());
    }

    public List<MemoDto> getMemosInRange(LocalDate start, LocalDate end, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        List<DayMemo> memos = repository.findByCalendarAndDateBetween(calendar, start, end);

        return memos.stream()
                .map(memo -> toDto(memo, currentUser))
                .collect(Collectors.toList());
    }

    private MemoDto toDto(DayMemo memo, User currentUser) {
        AuthorDto authorDto = new AuthorDto(
                memo.getAuthor().getId(),
                memo.getAuthor().getName(),
                memo.getAuthor().getNickname(),
                ColorTagUtil.resolve(memo.getAuthor())
        );

        boolean isOwn = memo.getAuthor().getId().equals(currentUser.getId());

        return new MemoDto(
                memo.getId(),
                memo.getDate(),
                memo.getMemo(),
                authorDto,
                memo.getCreatedAt(),
                memo.getUpdatedAt(),
                isOwn
        );
    }
}
