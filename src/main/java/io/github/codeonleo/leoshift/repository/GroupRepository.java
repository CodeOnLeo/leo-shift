package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.group.Group;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("select g from Group g where g.id = :id and g.deletedAt is null")
    Optional<Group> findActiveById(@Param("id") Long id);

    @Query("select g from Group g where g.owner.id = :userId and g.deletedAt is null order by g.name")
    List<Group> findOwnedBy(@Param("userId") Long userId);

    /**
     * 사용자가 해당 날짜에 소속돼 있던 그룹.
     *
     * <p>멤버십에 기간이 있으므로 "지금 소속"이 아니라 "그때 소속"을 물을 수 있다.
     * 지난달 프로젝트 화면을 열면 그때 있던 사람들로 재현된다.
     */
    @Query("""
            select g from Group g
              join GroupMember m on m.group = g
             where m.user.id = :userId
               and g.deletedAt is null
               and m.joinedOn <= :on
               and (m.leftOn is null or m.leftOn >= :on)
             order by g.name
            """)
    List<Group> findByMemberOn(@Param("userId") Long userId, @Param("on") LocalDate on);
}
