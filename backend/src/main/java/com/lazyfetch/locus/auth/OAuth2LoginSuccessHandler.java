package com.lazyfetch.locus.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final AuthService authService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(AuthService authService,
                                     @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.authService = authService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        AuthService.AuthResponse result = authService.loginWithGoogle((OAuth2User) authentication.getPrincipal());
        response.addHeader("Set-Cookie", result.refreshCookie().toString());
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/chat?oauth=success");
    }
}