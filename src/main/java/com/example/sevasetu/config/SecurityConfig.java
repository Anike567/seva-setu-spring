package com.example.sevasetu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/**",
        "/error",
    };

    // Matchers for both IPv4 and IPv6 localhost
    private static final IpAddressMatcher IPV4_LOCALHOST = new IpAddressMatcher("127.0.0.1");
    private static final IpAddressMatcher IPV6_LOCALHOST = new IpAddressMatcher("::1");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/data/**").access((authentication, context) -> {
                    var request = context.getRequest();
                    boolean isLocalhost = IPV4_LOCALHOST.matches(request) || IPV6_LOCALHOST.matches(request);
                    return new AuthorizationDecision(isLocalhost);
                })
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
