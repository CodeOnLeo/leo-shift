package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.user.UserIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /** 소셜 로그인은 provider + uid로 찾는다. 이메일 문자열로 계정을 연결하지 않는다. */
    Optional<UserIdentity> findByProviderAndProviderUid(UserIdentity.Provider provider, String providerUid);

    List<UserIdentity> findByUserId(Long userId);
}
