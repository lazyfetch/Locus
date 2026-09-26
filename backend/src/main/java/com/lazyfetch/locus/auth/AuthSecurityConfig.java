package com.lazyfetch.locus.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class AuthSecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final OAuth2LoginSuccessHandler oauthSuccessHandler;

    public AuthSecurityConfig(JwtAuthenticationFilter jwtFilter, OAuth2LoginSuccessHandler oauthSuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.oauthSuccessHandler = oauthSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                        "/oauth2/**", "/login/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth.successHandler(oauthSuccessHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}