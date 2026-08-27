package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 찾는다. <b>대소문자를 구분하지 않는다.</b>
     *
     * <p>이전 구현은 정확히 일치하는 조회만 해서, 대소문자가 다르게 입력된 초대가
     * 조용히 실패하거나 별개 계정이 만들어졌다. 유니크 인덱스도 {@code lower(email)}
     * 기준이므로 조회도 같은 기준이어야 한다.
     */
    @Query("select u from User u where lower(u.email) = lower(:email) and u.deletedAt is null")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email) and u.deletedAt is null")
    boolean existsByEmail(@Param("email") String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") Long id);
}
