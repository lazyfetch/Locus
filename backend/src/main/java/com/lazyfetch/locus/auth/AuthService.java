package com.lazyfetch.locus.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {
    public static final String REFRESH_COOKIE = "locus_refresh";
    private final AuthUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthUserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String name, String email, String password) {
        validateCredentials(name, email, password);
        if (users.findByEmailIgnoreCase(email.trim()).isPresent()) {
            throw new AuthException("An account with that email already exists");
        }
        AuthUser user = users.save(new AuthUser(name.trim(), email.trim().toLowerCase(), passwordEncoder.encode(password)));
        return issue(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        AuthUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AuthException("Invalid email or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Invalid email or password");
        }
        return issue(user);
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new AuthException("Refresh token missing");
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthException("Refresh token invalid"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Refresh token expired");
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);
        return issue(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokens.save(token);
        });
    }

    @Transactional
    public AuthResponse loginWithGoogle(OAuth2User principal) {
        String email = principal.getAttribute("email");
        String subject = principal.getAttribute("sub");
        String name = principal.getAttribute("name");
        if (email == null || subject == null) throw new AuthException("Google account did not provide an email");
        AuthUser user = users.findByGoogleSubject(subject).orElseGet(() ->
                users.findByEmailIgnoreCase(email).orElseGet(() -> new AuthUser(
                        name == null || name.isBlank() ? email.split("@")[0] : name,
                        email.toLowerCase(), null)));
        user.setGoogleSubject(subject);
        if (name != null && !name.isBlank()) user.setName(name);
        return issue(users.save(user));
    }

    public AuthResponse issue(AuthUser user) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(raw), Instant.now().plus(Duration.ofDays(30))));
        return new AuthResponse(jwtService.createAccessToken(user), userView(user), refreshCookie(raw));
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(false)
                .sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie refreshCookie(String raw) {
        return ResponseCookie.from(REFRESH_COOKIE, raw).httpOnly(true).secure(false)
                .sameSite("Lax").path("/").maxAge(Duration.ofDays(30)).build();
    }

    public Map<String, Object> userView(AuthUser user) {
        return Map.of("id", user.getId().toString(), "name", user.getName(), "email", user.getEmail());
    }

    private static void validateCredentials(String name, String email, String password) {
        if (name == null || name.isBlank() || email == null || !email.contains("@") || password == null || password.length() < 8) {
            throw new AuthException("Name, valid email, and password of at least 8 characters are required");
        }
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record AuthResponse(String accessToken, Map<String, Object> user, ResponseCookie refreshCookie) { }
    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }
}