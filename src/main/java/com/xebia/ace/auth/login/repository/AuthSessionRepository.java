package com.xebia.ace.auth.login.repository;

import com.xebia.ace.auth.login.domain.AuthSession;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findByUserId(UUID userId);

    default AuthSession createSession(UUID userId, String clientDeviceId, InetAddress clientIp,
                                      Instant issuedAt, Duration ttl) {
        AuthSession session = new AuthSession(UUID.randomUUID(), userId, issuedAt, issuedAt.plus(ttl),
                clientDeviceId, clientIp);
        return save(session);
    }
}
