package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.ShareGroupMember;
import io.github.codeonleo.leoshift.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareGroupMemberRepository extends JpaRepository<ShareGroupMember, Long> {

    @Query("SELECT sgm FROM ShareGroupMember sgm LEFT JOIN FETCH sgm.user WHERE sgm.group = :group ORDER BY sgm.id ASC")
    List<ShareGroupMember> findByGroup(@Param("group") ShareGroup group);

    @Query("SELECT sgm FROM ShareGroupMember sgm LEFT JOIN FETCH sgm.group g LEFT JOIN FETCH g.owner WHERE sgm.user = :user")
    List<ShareGroupMember> findByUser(@Param("user") User user);

    Optional<ShareGroupMember> findByGroupAndUser(ShareGroup group, User user);

    void deleteByGroup(ShareGroup group);
}
