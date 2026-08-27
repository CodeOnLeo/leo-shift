package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.external.ExternalEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalEventRepository extends JpaRepository<ExternalEvent, Long> {

    @Query("""
            select e from ExternalEvent e
             where e.source.id in :sourceIds
               and e.startsAt < :to
               and e.endsAt >= :from
             order by e.startsAt
            """)
    List<ExternalEvent> findInRange(@Param("sourceIds") List<Long> sourceIds,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Modifying
    @Query("delete from ExternalEvent e where e.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") Long sourceId);
}
