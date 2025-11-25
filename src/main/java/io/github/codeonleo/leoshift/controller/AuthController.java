package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.AuthRequest;
import io.github.codeonleo.leoshift.dto.AuthResponse;
import io.github.codeonleo.leoshift.dto.RefreshTokenRequest;
import io.github.codeonleo.leoshift.dto.SignupRequest;
import io.github.codeonleo.leoshift.security.UserPrincipal;
import io.github.codeonleo.leoshift.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        AuthResponse.UserInfo user = authService.getCurrentUser(userPrincipal.getId());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        // 현재는 클라이언트 측에서 토큰 삭제
        // 향후 Redis 기반 토큰 블랙리스트 구현 가능
        return ResponseEntity.ok().build();
    }
}
