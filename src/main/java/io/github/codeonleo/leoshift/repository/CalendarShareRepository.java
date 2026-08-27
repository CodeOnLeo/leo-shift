package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 접근 권한 판정의 핫패스다.
 *
 * <p><b>조회에는 반드시 {@code status = ACCEPTED} 조건을 넣어야 한다.</b>
 * 스키마의 인덱스가 그 조건의 부분 인덱스이기 때문이다. 이전 구현은 인덱스의
 * 조건과 쿼리의 조건이 어긋나서 매 요청마다 seq scan이 났다.
 */
public interface CalendarShareRepository extends JpaRepository<CalendarShare, Long> {

    /** 개인에게 직접 공유된 캘린더. */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar
             where s.granteeUser.id = :userId
               and s.status = 'ACCEPTED'
            """)
    List<CalendarShare> findAcceptedForUser(@Param("userId") Long userId);

    /** 그룹을 통해 공유된 캘린더. */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar
             where s.granteeGroup.id in :groupIds
               and s.status = 'ACCEPTED'
            """)
    List<CalendarShare> findAcceptedForGroups(@Param("groupIds") List<Long> groupIds);

    /** 특정 캘린더에 대한 이 사용자의 직접 공유. 권한 판정용. */
    @Query("""
            select s from CalendarShare s
             where s.calendar.id = :calendarId
               and s.granteeUser.id = :userId
               and s.status = 'ACCEPTED'
            """)
    Optional<CalendarShare> findAcceptedFor(@Param("calendarId") Long calendarId, @Param("userId") Long userId);

    /** 특정 캘린더에 대한 그룹 경유 공유. 권한 판정용. */
    @Query("""
            select s from CalendarShare s
             where s.calendar.id = :calendarId
               and s.granteeGroup.id in :groupIds
               and s.status = 'ACCEPTED'
            """)
    List<CalendarShare> findAcceptedFor(@Param("calendarId") Long calendarId, @Param("groupIds") List<Long> groupIds);

    /** 공유 관리 화면. 이 캘린더를 누가 어디까지 보고 있는가. */
    @Query("""
            select s from CalendarShare s
             where s.calendar.id = :calendarId
               and s.status <> 'REVOKED'
             order by s.createdAt
            """)
    List<CalendarShare> findLiveShares(@Param("calendarId") Long calendarId);

    /** 받은 초대 목록. */
    @Query("select s from CalendarShare s join fetch s.calendar where s.granteeUser.id = :userId and s.status = 'PENDING'")
    List<CalendarShare> findPendingInvitations(@Param("userId") Long userId);
}
