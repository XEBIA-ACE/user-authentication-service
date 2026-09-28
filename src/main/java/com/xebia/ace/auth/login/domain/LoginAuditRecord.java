package com.xebia.ace.auth.login.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_auth_audit_log")
public class LoginAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username_or_email", nullable = false, length = 256)
    private String usernameOrEmail;

    @Column(name = "client_device_id")
    private String clientDeviceId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "client_ip", columnDefinition = "inet")
    private InetAddress clientIp;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginAuditRecord() {
    }

    public LoginAuditRecord(UUID userId, String usernameOrEmail, String clientDeviceId, InetAddress clientIp,
                            boolean success, String reasonCode, Instant createdAt) {
        this.userId = userId;
        this.usernameOrEmail = usernameOrEmail;
        this.clientDeviceId = clientDeviceId;
        this.clientIp = clientIp;
        this.success = success;
        this.reasonCode = reasonCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public String getClientDeviceId() {
        return clientDeviceId;
    }

    public InetAddress getClientIp() {
        return clientIp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
