package io.github.codeonleo.leoshift.config;

import io.github.codeonleo.leoshift.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 개발 전용.
 *
 * <p>아직 로그인 수단이 없어서 {@code /api/**}가 전부 막혀 있다. 로컬에서는
 * 개발용 사용자로 자동 인증해서 화면을 볼 수 있게 한다.
 *
 * <p><b>{@code local} 프로파일에서만 활성화된다.</b> 다른 환경에서는 이 설정이
 * 아예 로딩되지 않으므로, 인증을 만들기 전까지 서버는 계속 잠겨 있다.
 * 이전 구현처럼 조용히 사용자 1번으로 폴백하는 일은 없다.
 */
@Configuration
@Profile("local")
public class LocalDevConfig {

    static final String DEV_EMAIL = "dev@localhost";

    @Bean
    SecurityFilterChain localFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new DevAuthFilter(userRepository), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 개발용 사용자로 SecurityContext를 채운다.
     *
     * <p>{@code X-Dev-User} 헤더로 다른 시드 사용자를 흉내 낼 수 있다. 공유와 그룹은
     * <b>혼자서는 확인할 수 없는 기능</b>이기 때문이다. 받은 공유를 수락하면 캘린더
     * 목록에 들어오는지, 공유를 끊으면 상대 화면에서 사라지는지는 사용자를 바꿔가며
     * 봐야 알 수 있다.
     *
     * <pre>curl -H 'X-Dev-User: sujin@localhost' localhost:8080/api/calendars</pre>
     *
     * <p>이 클래스 전체가 {@code local} 프로파일에서만 로딩되므로 다른 환경에는
     * 이 헤더를 읽는 코드가 아예 존재하지 않는다.
     */
    private static final class DevAuthFilter extends OncePerRequestFilter {

        private static final String IMPERSONATE_HEADER = "X-Dev-User";

        private final UserRepository userRepository;

        private DevAuthFilter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String requested = request.getHeader(IMPERSONATE_HEADER);
                String email = requested != null && !requested.isBlank() ? requested.trim() : DEV_EMAIL;

                userRepository.findByEmail(email).ifPresent(user ->
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user.getId(), null, AuthorityUtils.createAuthorityList("ROLE_USER"))));
            }
            chain.doFilter(request, response);
        }
    }
}
