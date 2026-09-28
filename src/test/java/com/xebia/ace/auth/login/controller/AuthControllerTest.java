package com.xebia.ace.auth.login.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xebia.ace.auth.login.dto.LoginErrorCode;
import com.xebia.ace.auth.login.dto.LoginRequestDto;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import com.xebia.ace.auth.login.dto.UserSummaryDto;
import com.xebia.ace.auth.login.service.AdminAuthenticationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(ClientIpResolver.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuthenticationService authenticationService;

    @Test
    void returnsSessionOnSuccessfulLogin() throws Exception {
        Instant issuedAt = Instant.parse("2026-01-01T10:00:00Z");
        when(authenticationService.login(any())).thenReturn(LoginResultDto.success("session-1",
                new UserSummaryDto("user-1", "admin", "admin@school.example"), issuedAt,
                issuedAt.plusSeconds(1800)));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.issuedAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:30:00Z"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void returnsOkWithGenericErrorOnFailedLogin() throws Exception {
        when(authenticationService.login(any())).thenReturn(
                LoginResultDto.failure(LoginErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.errorMessage").value(LoginResultDto.GENERIC_LOGIN_FAILURE_MESSAGE))
                .andExpect(jsonPath("$.sessionId").isEmpty())
                .andExpect(jsonPath("$.user").isEmpty());
    }

    @Test
    void returnsBadRequestWhenRequiredFieldMissing() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        verifyNoInteractions(authenticationService);
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        verifyNoInteractions(authenticationService);
    }

    @Test
    void returnsGenericInternalErrorOnUnexpectedFailure() throws Exception {
        when(authenticationService.login(any())).thenThrow(new IllegalStateException("db down: secret detail"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value(AuthExceptionHandler.INTERNAL_ERROR_MESSAGE));
    }

    @Test
    void derivesClientIpFromForwardedForHeaderWhenAbsentFromBody() throws Exception {
        when(authenticationService.login(any())).thenReturn(
                LoginResultDto.failure(LoginErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .content("{\"usernameOrEmail\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());

        verify(authenticationService).login(new LoginRequestDto("admin", "secret", null, "203.0.113.7"));
    }

    @Test
    void prefersClientIpFromBody() throws Exception {
        when(authenticationService.login(any())).thenReturn(
                LoginResultDto.failure(LoginErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.7")
                        .content("{\"usernameOrEmail\":\"admin\",\"password\":\"secret\","
                                + "\"clientDeviceId\":\"ios-1\",\"clientIpAddress\":\"198.51.100.2\"}"))
                .andExpect(status().isOk());

        verify(authenticationService).login(new LoginRequestDto("admin", "secret", "ios-1", "198.51.100.2"));
    }
}
