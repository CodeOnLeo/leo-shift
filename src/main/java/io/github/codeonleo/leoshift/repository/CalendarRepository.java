package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("select c from Calendar c where c.id = :id and c.deletedAt is null")
    Optional<Calendar> findActiveById(@Param("id") Long id);

    /**
     * 내 캘린더. <b>소유자를 함께 가져온다.</b>
     *
     * <p>{@code open-in-view=false}라 트랜잭션이 끝나면 프록시를 열 수 없다.
     * 목록 응답이 소유자 이름을 담게 되면서 여기서 미리 가져와야 한다.
     */
    @Query("""
            select c from Calendar c
              join fetch c.ownerUser
             where c.ownerUser.id = :userId and c.deletedAt is null
             order by c.isDefault desc, c.name
            """)
    List<Calendar> findOwnedBy(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerUser.id = :userId and c.isDefault = true and c.deletedAt is null")
    Optional<Calendar> findDefaultOf(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerUser.id = :userId and c.kind = 'WORK' and c.deletedAt is null order by c.id")
    List<Calendar> findWorkCalendarsOf(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerGroup.id = :groupId and c.deletedAt is null order by c.name")
    List<Calendar> findOwnedByGroup(@Param("groupId") Long groupId);
}
