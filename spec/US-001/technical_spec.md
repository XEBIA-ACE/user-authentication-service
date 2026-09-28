## S-002

1. Contracts & Interfaces

1.1 HTTP API Contract

Endpoint: POST /auth/login  
Purpose: Administrator interactive login (FR-001–FR-003, FR-012, FR-017)

Request body (application/json):

- usernameOrEmail: string, REQUIRED, non-empty (FR-006, FR-014, EC-001–EC-003)
- password: string, REQUIRED, non-empty (FR-001, FR-014, EC-002)
- clientDeviceId: string, OPTIONAL [NEEDS CLARIFICATION: Is clientDeviceId mandatory or optional?] (Assumed: optional)  
- clientIpAddress: string, OPTIONAL; if absent, server SHALL derive from X-Forwarded-For or remote addr

Response body (application/json):

- success: boolean (FR-002, FR-003, EC-007)
- errorCode: string | null (FR-004, FR-011, FR-016)
  - Possible values:
    - "INVALID_CREDENTIALS" (covers EC-001–EC-003, EC-006)
    - "UNAUTHORIZED" (inactive/locked; EC-004–EC-005)
  - MUST NOT distinguish username vs password cause (FR-005)
- errorMessage: string | null; English, generic and identical across all invalid cases (FR-004, FR-005, FR-016, EC-006)
- requiresAdditionalAction: boolean (always false for this flow; reserved for MFA etc.) (FR-011)
- sessionId: string | null (FR-012–FR-013, EC-007)
- user:
  - id: string
  - username: string
  - email: string
  This block MUST be present only when success = true (FR-018, FR-019)
- issuedAt: ISO-8601 datetime string | null
- expiresAt: ISO-8601 datetime string | null

HTTP status codes:

- 200 OK: login processed; success indicates grant/deny (FR-002, FR-003)
- 400 Bad Request: structurally invalid JSON or missing required fields (FR-014)
- 500 Internal Server Error: unexpected server issues

1.2 Data Model

Database: authDb (PostgreSQL)

Tables (new or confirmed):

1) administrator_users

- id UUID PRIMARY KEY
- username VARCHAR(128) UNIQUE NOT NULL
- email VARCHAR(256) UNIQUE NOT NULL
- password_hash VARCHAR(255) NOT NULL
- is_active BOOLEAN NOT NULL DEFAULT TRUE (FR-007, EC-004)
- is_locked BOOLEAN NOT NULL DEFAULT FALSE (FR-015, EC-005)
- failed_login_count INTEGER NOT NULL DEFAULT 0 (FR-010)
- last_login_at TIMESTAMPTZ NULL (FR-009, EC-008)
- created_at TIMESTAMPTZ NOT NULL DEFAULT now()
- updated_at TIMESTAMPTZ NOT NULL DEFAULT now()

Indexes:

- IX_admin_users_login_identifier ON administrator_users (lower(username), lower(email)) (FR-006, FR-001)

2) auth_sessions

- id UUID PRIMARY KEY
- user_id UUID NOT NULL REFERENCES administrator_users(id) ON DELETE CASCADE (FR-013)
- issued_at TIMESTAMPTZ NOT NULL
- expires_at TIMESTAMPTZ NOT NULL
- client_device_id VARCHAR(255) NULL
- client_ip INET NULL
- created_at TIMESTAMPTZ NOT NULL DEFAULT now()

Indexes:

- IX_auth_sessions_user_id ON auth_sessions(user_id)
- IX_auth_sessions_expires_at ON auth_sessions(expires_at) (for cleanup jobs)

3) admin_auth_audit_log (for optional audit; FR-020)

- id BIGSERIAL PRIMARY KEY
- user_id UUID NULL REFERENCES administrator_users(id)
- username_or_email VARCHAR(256) NOT NULL
- client_device_id VARCHAR(255) NULL
- client_ip INET NULL
- success BOOLEAN NOT NULL
- reason_code VARCHAR(64) NOT NULL  -- e.g. INVALID_CREDENTIALS, UNAUTHORIZED
- created_at TIMESTAMPTZ NOT NULL DEFAULT now()

1.3 Internal Interfaces

Java/Kotlin-style contracts (language-agnostic but concrete):

Interface: AdminAuthenticationService

- LoginResultDto login(LoginRequestDto request);

Class: LoginRequestDto

- String usernameOrEmail;
- String password;
- String clientDeviceId;
- String clientIpAddress;

Class: LoginResultDto

- boolean success;
- String errorCode;
- String errorMessage;
- boolean requiresAdditionalAction;
- String sessionId;
- UserSummaryDto user;
- Instant issuedAt;
- Instant expiresAt;

Class: UserSummaryDto

- String id;
- String username;
- String email;

Repository interfaces:

- AdministratorUserRepository
  - Optional<AdministratorUser> findByUsernameOrEmailIgnoreCase(String identifier);
  - void incrementFailedLoginCount(UUID userId);
  - void resetFailedLoginCountAndUpdateLastLogin(UUID userId, Instant loginTime); (FR-009–FR-010)
- AuthSessionRepository
  - AuthSession createSession(UUID userId, String clientDeviceId, InetAddress clientIp, Duration ttl);
- AdminAuthAuditLogRepository
  - void save(LoginAuditRecord record);

Password hashing interface:

- PasswordHasher
  - boolean matches(String rawPassword, String passwordHash);


2. Test Strategy

2.1 API Contract Tests (integration)

- T-API-001: Successful login (EC-007, FR-001–FR-003, FR-007, FR-012–FR-013, FR-018–FR-019)
  - Request: valid usernameOrEmail + correct password, active, unlocked user
  -