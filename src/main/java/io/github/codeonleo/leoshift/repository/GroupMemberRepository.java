package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.group.GroupMember;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /** 현재 소속 중인 멤버십. 활성은 하나뿐이다. */
    @Query("select m from GroupMember m where m.group.id = :groupId and m.user.id = :userId and m.leftOn is null")
    Optional<GroupMember> findActive(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 해당 기간에 이 그룹에 소속됐던 사람들.
     *
     * <p>그룹 타임라인 화면이 쓰는 조회다. 기간이 겹치기만 하면 포함되므로
     * 월 중간에 합류하거나 나간 사람도 그 달 화면에 나온다.
     */
    @Query("""
            select m from GroupMember m
              join fetch m.user
             where m.group.id = :groupId
               and m.joinedOn <= :to
               and (m.leftOn is null or m.leftOn >= :from)
             order by m.joinedOn
            """)
    List<GroupMember> findOverlapping(@Param("groupId") Long groupId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query("select m from GroupMember m join fetch m.user where m.group.id = :groupId and m.leftOn is null")
    List<GroupMember> findCurrentMembers(@Param("groupId") Long groupId);

    /**
     * 관리 화면용. 나간 사람도 포함한 전체 이력.
     *
     * <p>나간 사람을 감추면 "이 사람 언제까지 있었지?"를 확인할 수 없고,
     * 기간을 잘못 적었을 때 되돌릴 방법도 없어진다.
     */
    @Query("""
            select m from GroupMember m
              join fetch m.user
             where m.group.id = :groupId
             order by case when m.leftOn is null then 0 else 1 end, m.joinedOn, m.id
            """)
    List<GroupMember> findAllOf(@Param("groupId") Long groupId);

    /** 목록 화면의 인원수. 그룹마다 세면 N+1이 된다. */
    @Query("""
            select m.group.id, count(m)
              from GroupMember m
             where m.group.id in :groupIds and m.leftOn is null
             group by m.group.id
            """)
    List<Object[]> countCurrentMembers(@Param("groupIds") List<Long> groupIds);
}
