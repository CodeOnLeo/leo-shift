package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.AuthRequest;
import io.github.codeonleo.leoshift.dto.AuthResponse;
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
}
