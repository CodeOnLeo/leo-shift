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
}
