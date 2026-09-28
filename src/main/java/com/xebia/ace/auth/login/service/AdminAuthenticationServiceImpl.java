package com.xebia.ace.auth.login.service;

import com.xebia.ace.auth.config.AuthSessionProperties;
import com.xebia.ace.auth.login.domain.AdministratorUser;
import com.xebia.ace.auth.login.domain.AuthSession;
import com.xebia.ace.auth.login.domain.LoginAuditRecord;
import com.xebia.ace.auth.login.dto.LoginErrorCode;
import com.xebia.ace.auth.login.dto.LoginRequestDto;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import com.xebia.ace.auth.login.dto.UserSummaryDto;
import com.xebia.ace.auth.login.repository.AdminAuthAuditLogRepository;
import com.xebia.ace.auth.login.repository.AdministratorUserRepository;
import com.xebia.ace.auth.login.repository.AuthSessionRepository;
import com.xebia.ace.auth.login.security.PasswordHasher;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthenticationServiceImpl implements AdminAuthenticationService {

    static final int MAX_IDENTIFIER_LENGTH = 256;
    static final int MAX_PASSWORD_LENGTH = 256;
    static final String SUCCESS_REASON_CODE = "SUCCESS";

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationServiceImpl.class);

    private final AdministratorUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AdminAuthAuditLogRepository auditLogRepository;
    private final PasswordHasher passwordHasher;
    private final AuthSessionProperties sessionProperties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AdminAuthenticationServiceImpl(AdministratorUserRepository userRepository,
                                          AuthSessionRepository sessionRepository,
                                          AdminAuthAuditLogRepository auditLogRepository,
                                          PasswordHasher passwordHasher,
                                          AuthSessionProperties sessionProperties,
                                          Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordHasher = passwordHasher;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public LoginResultDto login(LoginRequestDto request) {
        String identifier = request.usernameOrEmail() == null ? "" : request.usernameOrEmail().strip();
        String password = request.password();
        InetAddress clientIp = IpAddresses.parseLiteral(request.clientIpAddress()).orElse(null);
        String clientDeviceId = request.clientDeviceId();

        if (!isWellFormed(identifier, password)) {
            return fail(null, identifier, clientDeviceId, clientIp, LoginErrorCode.INVALID_CREDENTIALS);
        }

        Optional<AdministratorUser> candidate = userRepository.findByUsernameOrEmailIgnoreCase(identifier);
        if (candidate.isEmpty()) {
            // Equalise response time with the existing-account path to avoid account enumeration.
            passwordHasher.matches(password, dummyPasswordHash);
            return fail(null, identifier, clientDeviceId, clientIp, LoginErrorCode.INVALID_CREDENTIALS);
        }

        AdministratorUser user = candidate.get();
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            userRepository.incrementFailedLoginCount(user.getId());
            return fail(user.getId(), identifier, clientDeviceId, clientIp, LoginErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive() || user.isLocked()) {
            return fail(user.getId(), identifier, clientDeviceId, clientIp, LoginErrorCode.UNAUTHORIZED);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        userRepository.resetFailedLoginCountAndUpdateLastLogin(user.getId(), now);
        AuthSession session = sessionRepository.createSession(
                user.getId(), clientDeviceId, clientIp, now, sessionProperties.ttl());
        audit(user.getId(), identifier, clientDeviceId, clientIp, true, SUCCESS_REASON_CODE);
        log.info("Administrator login succeeded userId={}", user.getId());

        return LoginResultDto.success(
                session.getId().toString(),
                new UserSummaryDto(user.getId().toString(), user.getUsername(), user.getEmail()),
                session.getIssuedAt(),
                session.getExpiresAt());
    }

    private static boolean isWellFormed(String identifier, String password) {
        return !identifier.isEmpty()
                && identifier.length() <= MAX_IDENTIFIER_LENGTH
                && password != null
                && !password.isEmpty()
                && password.length() <= MAX_PASSWORD_LENGTH;
    }

    private LoginResultDto fail(UUID userId, String identifier, String clientDeviceId, InetAddress clientIp,
                                LoginErrorCode errorCode) {
        audit(userId, identifier, clientDeviceId, clientIp, false, errorCode.name());
        log.info("Administrator login failed userId={} reason={}", userId, errorCode);
        return LoginResultDto.failure(errorCode);
    }

    private void audit(UUID userId, String identifier, String clientDeviceId, InetAddress clientIp,
                       boolean success, String reasonCode) {
        String auditedIdentifier = identifier.length() > MAX_IDENTIFIER_LENGTH
                ? identifier.substring(0, MAX_IDENTIFIER_LENGTH)
                : identifier;
        auditLogRepository.save(new LoginAuditRecord(
                userId, auditedIdentifier, clientDeviceId, clientIp, success, reasonCode, clock.instant()));
    }
}
