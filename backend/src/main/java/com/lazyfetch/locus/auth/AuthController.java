package com.lazyfetch.locus.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthUserRepository users;

    public AuthController(AuthService auth, AuthUserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Credentials request, HttpServletResponse response) {
        return issue(auth.register(request.name(), request.email(), request.password()), response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return issue(auth.login(request.email(), request.password()), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String token,
                                                       HttpServletResponse response) {
        return issue(auth.refresh(token), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String token,
                                       HttpServletResponse response) {
        auth.logout(token);
        response.addHeader("Set-Cookie", auth.clearRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal String userId) {
        AuthUser user = users.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new AuthService.AuthException("User not found"));
        return Map.of("user", auth.userView(user));
    }

    private ResponseEntity<Map<String, Object>> issue(AuthService.AuthResponse result, HttpServletResponse response) {
        response.addHeader("Set-Cookie", result.refreshCookie().toString());
        return ResponseEntity.ok(Map.of("accessToken", result.accessToken(), "user", result.user()));
    }

    public record Credentials(String name, String email, String password) { }
    public record LoginRequest(String email, String password) { }
}