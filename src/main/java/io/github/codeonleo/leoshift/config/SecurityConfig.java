package io.github.codeonleo.leoshift.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 기본 보안 설정.
 *
 * <p><b>아직 로그인 수단이 없다.</b> 그래서 {@code /api/**}는 전부 거부된다.
 * 이게 옳은 기본값이다 — 인증을 만들기 전까지는 아무도 들어오지 못해야 한다.
 * 로컬 개발은 {@code local} 프로파일이 개발용 사용자로 자동 인증한다.
 *
 * <p>TODO 인증을 붙일 때 함께 처리할 것
 * <ul>
 *   <li>토큰을 HttpOnly 쿠키로 발급. localStorage에 두지 않는다</li>
 *   <li>쿠키 인증이 되면 CSRF 보호를 다시 켠다 (지금은 인증 자체가 없어 무의미)</li>
 *   <li>보안 헤더 — CSP, Referrer-Policy</li>
 *   <li>회원가입은 초대 코드가 있어야만</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** SPA 자산. 인증 없이 내려간다. */
    static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/favicon.ico",
            "/assets/**", "/icons/**",
            "/manifest.webmanifest", "/sw.js", "/registerSW.js", "/workbox-*.js"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        // 읽기 전용 .ics 구독 주소. 구글 캘린더 서버가 읽어가므로
                        // 쿠키도 헤더도 붙일 수 없다. 권한은 주소 안의 토큰이 판단한다.
                        .requestMatchers(HttpMethod.GET, "/feed/*.ics").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // SPA 라우트(/month, /day/... )는 index.html로 넘어간다
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, ex) ->
                                response.sendError(HttpStatus.FORBIDDEN.value())));
        return http.build();
    }
}
