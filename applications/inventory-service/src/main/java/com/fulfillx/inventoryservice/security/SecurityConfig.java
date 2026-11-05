package com.fulfillx.inventoryservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authorization rules for this phase:
 * <ul>
 *   <li>{@code /actuator/health} — public.</li>
 *   <li>{@code POST /api/v1/products} and stock adjustment — {@code ADMIN} only.</li>
 *   <li>Product/inventory reads — any authenticated caller.</li>
 *   <li>Reservation create/release — {@code OPERATOR} or {@code ADMIN}.
 *       There is no service-to-service authentication yet, so the internal
 *       roles stand in for "the order flow"; a {@code CUSTOMER} must never
 *       reserve or release directly (reservations have no customer owner).</li>
 * </ul>
 * inventory-service never issues JWTs itself; it only validates tokens
 * auth-service already issued (see {@link JwtService}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtService jwtService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/*/adjust").hasRole("ADMIN")
                        // Reservations carry no customer owner, so "any authenticated caller"
                        // let a CUSTOMER release another customer's reservation or hold stock
                        // without an order. Until service-to-service auth exists, only the
                        // internal roles may reserve or release.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/inventory/reservations", "/api/v1/inventory/reservations/*/release")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
