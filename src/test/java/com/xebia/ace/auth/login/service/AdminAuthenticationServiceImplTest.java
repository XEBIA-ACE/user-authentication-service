package com.xebia.ace.auth.login.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xebia.ace.auth.config.AuthSessionProperties;
import com.xebia.ace.auth.login.domain.AdministratorUser;
import com.xebia.ace.auth.login.domain.AuthSession;
import com.xebia.ace.auth.login.domain.LoginAuditRecord;
import com.xebia.ace.auth.login.dto.LoginRequestDto;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import com.xebia.ace.auth.login.repository.AdminAuthAuditLogRepository;
import com.xebia.ace.auth.login.repository.AdministratorUserRepository;
import com.xebia.ace.auth.login.repository.AuthSessionRepository;
import com.xebia.ace.auth.login.security.PasswordHasher;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthenticationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String PASSWORD = "Correct-Horse-1";
    private static final String HASH = "stored-hash";

    @Mock
    private AdministratorUserRepository userRepository;
    @Mock
    private AuthSessionRepository sessionRepository;
    @Mock
    private AdminAuthAuditLogRepository auditLogRepository;
    @Mock
    private PasswordHasher passwordHasher;

    private AdminAuthenticationServiceImpl service;
    private AdministratorUser admin;

    @BeforeEach
    void setUp() {
        when(passwordHasher.hash(anyString())).thenReturn("dummy-hash");
        service = new AdminAuthenticationServiceImpl(userRepository, sessionRepository, auditLogRepository,
                passwordHasher, new AuthSessionProperties(TTL), Clock.fixed(NOW, ZoneOffset.UTC));
        admin = new AdministratorUser(UUID.randomUUID(), "admin", "admin@school.example", HASH);
    }

    @Test
    void grantsAccessAndEstablishesSessionForValidCredentials() {
        when(userRepository.findByUsernameOrEmailIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.createSession(eq(admin.getId()), eq("device-1"), any(InetAddress.class), eq(NOW),
                eq(TTL))).thenReturn(new AuthSession(sessionId, admin.getId(), NOW, NOW.plus(TTL), "device-1", null));

        LoginResultDto result = service.login(new LoginRequestDto(" admin ", PASSWORD, "device-1", "10.0.0.1"));

        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.requiresAdditionalAction()).isFalse();
        assertThat(result.sessionId()).isEqualTo(sessionId.toString());
        assertThat(result.user().id()).isEqualTo(admin.getId().toString());
        assertThat(result.user().username()).isEqualTo("admin");
        assertThat(result.user().email()).isEqualTo("admin@school.example");
        assertThat(result.issuedAt()).isEqualTo(NOW);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(TTL));
        verify(userRepository).resetFailedLoginCountAndUpdateLastLogin(admin.getId(), NOW);
        verify(userRepository, never()).incrementFailedLoginCount(any());

        LoginAuditRecord audit = capturedAudit();
        assertThat(audit.isSuccess()).isTrue();
        assertThat(audit.getReasonCode()).isEqualTo("SUCCESS");
        assertThat(audit.getUserId()).isEqualTo(admin.getId());
    }

    @Test
    void deniesAccessAndIncrementsFailedCountForWrongPassword() {
        when(userRepository.findByUsernameOrEmailIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        LoginResultDto result = service.login(new LoginRequestDto("admin", "wrong", null, null));

        assertGenericFailure(result, "INVALID_CREDENTIALS");
        verify(userRepository).incrementFailedLoginCount(admin.getId());
        verify(sessionRepository, never()).createSession(any(), any(), any(), any(), any());
        assertThat(capturedAudit().getReasonCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void treatsUnknownAccountAsInvalidCredentialsAndStillHashes() {
        when(userRepository.findByUsernameOrEmailIgnoreCase("ghost")).thenReturn(Optional.empty());

        LoginResultDto result = service.login(new LoginRequestDto("ghost", PASSWORD, null, null));

        assertGenericFailure(result, "INVALID_CREDENTIALS");
        verify(passwordHasher).matches(PASSWORD, "dummy-hash");
        verify(userRepository, never()).incrementFailedLoginCount(any());
        assertThat(capturedAudit().getUserId()).isNull();
    }

    @Test
    void rejectsBlankIdentifierWithoutLookup() {
        LoginResultDto result = service.login(new LoginRequestDto("   ", PASSWORD, null, null));

        assertGenericFailure(result, "INVALID_CREDENTIALS");
        verify(userRepository, never()).findByUsernameOrEmailIgnoreCase(anyString());
    }

    @Test
    void rejectsEmptyPasswordWithoutLookup() {
        LoginResultDto result = service.login(new LoginRequestDto("admin", "", null, null));

        assertGenericFailure(result, "INVALID_CREDENTIALS");
        verify(userRepository, never()).findByUsernameOrEmailIgnoreCase(anyString());
    }

    @Test
    void rejectsOverlongIdentifierWithoutLookup() {
        LoginResultDto result = service.login(new LoginRequestDto("a".repeat(257), PASSWORD, null, null));

        assertGenericFailure(result, "INVALID_CREDENTIALS");
        verify(userRepository, never()).findByUsernameOrEmailIgnoreCase(anyString());
        assertThat(capturedAudit().getUsernameOrEmail()).hasSize(256);
    }

    @Test
    void deniesInactiveAccountEvenWithCorrectPassword() {
        admin.setActive(false);
        when(userRepository.findByUsernameOrEmailIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);

        LoginResultDto result = service.login(new LoginRequestDto("admin", PASSWORD, null, null));

        assertGenericFailure(result, "UNAUTHORIZED");
        verify(sessionRepository, never()).createSession(any(), any(), any(), any(), any());
        verify(userRepository, never()).resetFailedLoginCountAndUpdateLastLogin(any(), any());
    }

    @Test
    void deniesLockedAccountEvenWithCorrectPassword() {
        admin.setLocked(true);
        when(userRepository.findByUsernameOrEmailIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);

        LoginResultDto result = service.login(new LoginRequestDto("admin", PASSWORD, null, null));

        assertGenericFailure(result, "UNAUTHORIZED");
        verify(sessionRepository, never()).createSession(any(), any(), any(), any(), any());
    }

    @Test
    void ignoresUnparseableClientIp() {
        when(userRepository.findByUsernameOrEmailIgnoreCase("ghost")).thenReturn(Optional.empty());

        service.login(new LoginRequestDto("ghost", PASSWORD, null, "not-an-ip.example.com"));

        assertThat(capturedAudit().getClientIp()).isNull();
    }

    private LoginAuditRecord capturedAudit() {
        ArgumentCaptor<LoginAuditRecord> captor = ArgumentCaptor.forClass(LoginAuditRecord.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertGenericFailure(LoginResultDto result, String expectedCode) {
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(expectedCode);
        assertThat(result.errorMessage()).isEqualTo(LoginResultDto.GENERIC_LOGIN_FAILURE_MESSAGE);
        assertThat(result.sessionId()).isNull();
        assertThat(result.user()).isNull();
        assertThat(result.issuedAt()).isNull();
        assertThat(result.expiresAt()).isNull();
    }
}
