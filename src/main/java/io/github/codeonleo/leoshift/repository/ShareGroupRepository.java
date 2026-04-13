package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareGroupRepository extends JpaRepository<ShareGroup, Long> {

    @Query("SELECT sg FROM ShareGroup sg LEFT JOIN FETCH sg.owner WHERE sg.owner = :owner ORDER BY sg.name ASC")
    List<ShareGroup> findByOwnerOrderByNameAsc(@Param("owner") User owner);

    Optional<ShareGroup> findByOwnerAndName(User owner, String name);
}
