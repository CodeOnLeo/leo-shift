package io.github.codeonleo.leoshift.security;

import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 요청의 사용자.
 *
 * <p><b>인증되지 않았으면 예외를 던진다.</b> 이전 구현은 인증이 없을 때 사용자 1번으로
 * 폴백했고, 인증 필터도 예외를 삼키고 통과시켰다. permitAll 경로를 하나만 잘못
 * 추가해도 남의 계정으로 실행될 수 있는 구조였다.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new NotAuthenticatedException();
        }
    }

    public User require() {
        Long id = id();
        return userRepository.findActiveById(id).orElseThrow(NotAuthenticatedException::new);
    }

    public static class NotAuthenticatedException extends RuntimeException {
        public NotAuthenticatedException() {
            super("인증이 필요합니다");
        }
    }
}
