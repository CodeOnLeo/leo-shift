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

    @Query("select c from Calendar c where c.ownerUser.id = :userId and c.deletedAt is null order by c.isDefault desc, c.name")
    List<Calendar> findOwnedBy(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerUser.id = :userId and c.isDefault = true and c.deletedAt is null")
    Optional<Calendar> findDefaultOf(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerUser.id = :userId and c.kind = 'WORK' and c.deletedAt is null order by c.id")
    List<Calendar> findWorkCalendarsOf(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.ownerGroup.id = :groupId and c.deletedAt is null order by c.name")
    List<Calendar> findOwnedByGroup(@Param("groupId") Long groupId);
}
