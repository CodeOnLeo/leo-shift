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
              join fetch s.calendar c
              left join fetch c.ownerUser
             where s.granteeUser.id = :userId
               and s.status = 'ACCEPTED'
            """)
    List<CalendarShare> findAcceptedForUser(@Param("userId") Long userId);

    /**
     * 그룹을 통해 공유된 캘린더.
     *
     * <p><b>소유자가 지금도 그 그룹에 있어야 한다.</b> 공유 행만 보면 프로젝트를 떠난
     * 사람의 캘린더가 남은 사람들의 목록에 계속 남는다. 관계가 끝났는데 접근이
     * 남아 있으면 안 된다.
     *
     * <p>그렇다고 나갈 때 공유를 취소하지는 않는다. 취소해 버리면 <b>그 사람이 있던
     * 기간의 기록까지 사라져</b> "그때 있던 사람들로 재현한다"는 설계가 깨진다.
     * 그래서 이렇게 나뉜다 — 날짜 개념이 없는 권한 판정은 <i>현재</i> 소속으로,
     * 기간을 아는 그룹 타임라인은 <i>그때</i>의 소속으로 판단한다.
     *
     * <p>다시 들어오면 예전 공유가 그대로 되살아난다. 공유를 지운 적이 없으므로
     * 그게 맞고, 원치 않으면 공유 관리 화면에서 끊으면 된다.
     */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar c
              left join fetch c.ownerUser
             where s.granteeGroup.id in :groupIds
               and s.status = 'ACCEPTED'
               and (c.ownerUser is null
                    or exists (select 1 from GroupMember m
                                where m.group.id = s.granteeGroup.id
                                  and m.user.id = c.ownerUser.id
                                  and m.leftOn is null))
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
               and (s.calendar.ownerUser is null
                    or exists (select 1 from GroupMember m
                                where m.group.id = s.granteeGroup.id
                                  and m.user.id = s.calendar.ownerUser.id
                                  and m.leftOn is null))
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

    /**
     * 공유 관리 화면. <b>내 캘린더 전부</b>에 걸린 살아 있는 공유를 한 번에.
     *
     * <p>화면은 캘린더별이 아니라 대상별로 읽힌다("직장 · 근무만 · 5명").
     * 한 대상에 걸린 여러 캘린더의 공유를 모아 단계를 계산해야 하므로
     * 캘린더마다 조회하면 안 된다.
     */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar c
              left join fetch s.granteeUser
              left join fetch s.granteeGroup
             where c.ownerUser.id = :userId
               and c.deletedAt is null
               and s.status <> 'REVOKED'
             order by s.createdAt, s.id
            """)
    List<CalendarShare> findLiveSharesOfOwner(@Param("userId") Long userId);

    /** 취소되지 않은 공유 한 건. 같은 대상에 두 건이 생기지 않게 하려면 여기서 먼저 찾는다. */
    @Query("""
            select s from CalendarShare s
             where s.calendar.id = :calendarId
               and s.granteeUser.id = :userId
               and s.status <> 'REVOKED'
            """)
    Optional<CalendarShare> findLiveUserShare(@Param("calendarId") Long calendarId,
                                              @Param("userId") Long userId);

    @Query("""
            select s from CalendarShare s
             where s.calendar.id = :calendarId
               and s.granteeGroup.id = :groupId
               and s.status <> 'REVOKED'
            """)
    Optional<CalendarShare> findLiveGroupShare(@Param("calendarId") Long calendarId,
                                               @Param("groupId") Long groupId);

    /**
     * 그룹 타임라인. 이 그룹에 공유된 멤버들의 캘린더를 한 번에.
     *
     * <p>사람마다 조회하면 프로젝트 인원수만큼 질의가 늘어난다.
     */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar c
              join fetch c.ownerUser
             where s.granteeGroup.id = :groupId
               and s.status = 'ACCEPTED'
               and c.deletedAt is null
               and c.ownerUser.id in :userIds
            """)
    List<CalendarShare> findAcceptedForGroupByOwners(@Param("groupId") Long groupId,
                                                     @Param("userIds") List<Long> userIds);

    /** 받은 초대 목록. 누가 무엇을 보내왔는지 보여주려면 소유자까지 필요하다. */
    @Query("""
            select s from CalendarShare s
              join fetch s.calendar c
              join fetch c.ownerUser
             where s.granteeUser.id = :userId
               and s.status = 'PENDING'
             order by s.createdAt
            """)
    List<CalendarShare> findPendingInvitations(@Param("userId") Long userId);
}
