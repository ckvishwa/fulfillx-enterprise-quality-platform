package com.fulfillx.inventoryservice.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fulfillx.inventoryservice.reservation.api.ReservationController;
import com.fulfillx.inventoryservice.security.JwtProperties;
import com.fulfillx.inventoryservice.security.JwtService;
import com.fulfillx.inventoryservice.security.RestAccessDeniedHandler;
import com.fulfillx.inventoryservice.security.RestAuthenticationEntryPoint;
import com.fulfillx.inventoryservice.security.SecurityConfig;
import com.fulfillx.inventoryservice.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authorization rules for reservation create/release, exercised through the
 * real {@link SecurityConfig} filter chain and JWT validation. The service
 * layer is mocked: this test is about who may call, not about stock
 * arithmetic (which the Testcontainers-backed tests cover).
 *
 * <p>Reservations carry no customer owner, so any caller allowed to release
 * one can release anyone's. Before this test, a CUSTOMER token could release
 * another customer's reservation by UUID and reserve arbitrary stock.
 */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
class ReservationAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private static String body() {
        return """
                {"orderReference":"%s","productId":"%s","quantity":1,"idempotencyKey":"k-%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static InventoryReservation reservation() {
        InventoryReservation reservation = new InventoryReservation(UUID.randomUUID(), UUID.randomUUID(), 1, "k", "c");
        ReflectionTestUtils.setField(reservation, "id", UUID.randomUUID());
        return reservation;
    }

    @Test
    void customerCannotReleaseAReservation() throws Exception {
        UUID someoneElsesReservation = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/reservations/" + someoneElsesReservation + "/release")
                        .header("Authorization", "Bearer " + TestTokens.customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(reservationService, never()).releaseReservation(any());
    }

    @Test
    void customerCannotCreateAReservation() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());

        verify(reservationService, never()).createReservation(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "ADMIN"})
    void internalRolesCanCreateAndRelease(String role) throws Exception {
        InventoryReservation reservation = reservation();
        when(reservationService.createReservation(any(), any())).thenReturn(new ReservationOutcome(reservation, true));
        when(reservationService.releaseReservation(eq(reservation.getId()))).thenReturn(reservation);

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("Authorization", "Bearer " + TestTokens.tokenFor(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservation.getId() + "/release")
                        .header("Authorization", "Bearer " + TestTokens.tokenFor(role)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAndForgedTokensAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations/" + UUID.randomUUID() + "/release"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/inventory/reservations/" + UUID.randomUUID() + "/release")
                        .header("Authorization", "Bearer " + TestTokens.expiredToken("ADMIN")))
                .andExpect(status().isUnauthorized());
        String adminToken = TestTokens.adminToken();
        String tampered = adminToken.substring(0, adminToken.length() - 2)
                + (adminToken.endsWith("AA") ? "BB" : "AA");
        mockMvc.perform(post("/api/v1/inventory/reservations/" + UUID.randomUUID() + "/release")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).releaseReservation(any());
    }
}
