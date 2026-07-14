package com.fulfillx.inventoryservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the authenticated principal from a {@code Bearer} JWT issued by
 * auth-service, if present and valid. Deliberately never rejects a request
 * itself: an absent, expired, or tampered token just leaves the request
 * anonymous. The access decision (and uniform rejection) happens later in
 * {@code SecurityConfig} / {@code RestAuthenticationEntryPoint} — mirrors
 * auth-service's JwtAuthenticationFilter exactly, since the failure-surface
 * uniformity reasoning applies identically here.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.validateAndParse(token);
                String userId = claims.getSubject();
                String role = JwtService.roleClaim(claims);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // Never log token content or the specific failure reason
                // at a level that reaches production logs by default.
                log.debug("Rejected JWT: {}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
