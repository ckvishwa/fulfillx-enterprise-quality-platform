package com.fulfillx.authservice.security;

import com.fulfillx.authservice.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ensures unauthenticated access to a protected endpoint gets the same
 * JSON error contract as every other failure, instead of Spring Security's
 * default (empty 401 body / container error page). This is what actually
 * fires for a missing, expired, or tampered JWT, since
 * {@link JwtAuthenticationFilter} never rejects a request itself — it only
 * leaves it anonymous, and the access decision (and this entry point) does
 * the rejecting.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(401, "UNAUTHENTICATED", "Authentication is required.");
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
