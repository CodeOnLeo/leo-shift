package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.AuthRequest;
import io.github.codeonleo.leoshift.dto.AuthResponse;
import io.github.codeonleo.leoshift.dto.SignupRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.UserSettingsRepository;
import io.github.codeonleo.leoshift.security.JwtTokenProvider;
import io.github.codeonleo.leoshift.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(User.AuthProvider.LOCAL)
                .roles(Set.of(User.Role.USER))
                .enabled(true)
                .build();

        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        // 기본 캘린더 생성
        Calendar defaultCalendar = Calendar.builder()
                .owner(user)
                .name("내 근무표")
                .description("기본 근무표")
                .build();
        defaultCalendar = calendarRepository.save(defaultCalendar);

        // 사용자 설정 생성
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setDefaultCalendar(defaultCalendar);
        settings.setDefaultNotificationMinutes(60);
        userSettingsRepository.save(settings);

        // JWT 토큰 생성을 위한 Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user),
                null,
                UserPrincipal.create(user).getAuthorities()
        );

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .build())
                .build();
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // 마지막 로그인 시간 업데이트
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserInfo.builder()
                        .id(userPrincipal.getId())
                        .email(userPrincipal.getEmail())
                        .name(userPrincipal.getName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
