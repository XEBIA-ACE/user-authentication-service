# AGENTS.md — User Authentication Service

> **Purpose:** This file is the authoritative scaffold specification for the User Authentication Service. Every AI agent working on this repository must read and follow these instructions before writing a single line of code.

---

## 1. Stack

| Technology / Library | Role |
|---|---|
| **Java 21** (LTS) | Primary language; use records, sealed classes, pattern matching where appropriate |
| **Spring Boot 3.x** | Application framework; auto-configuration, embedded Tomcat, actuator |
| **Spring Security 6.x** | Security filter chain, authentication manager, method-level security |
| **Spring Authorization Server 1.x** | OAuth2 Authorization Server (authorization code, client credentials, refresh token flows) |
| **Spring Data JPA** | Repository layer; entity mapping to relational DB |
| **PostgreSQL 16** | Primary persistence store for users, roles, sessions, tokens |
| **Flyway** | Database schema versioning and migration |
| **Redis 7** | Session store and refresh-token / blacklist cache |
| **Spring Data Redis** | Redis integration via `RedisTemplate` / `ReactiveRedisTemplate` |
| **Spring Mail** | Password-recovery email dispatch |
| **MapStruct 1.5** | DTO ↔ entity mapping; compile-time, no reflection |
| **Lombok** | Boilerplate reduction (`@Builder`, `@Slf4j`, etc.) — only on non-record classes |
| **Springdoc OpenAPI 2.x** | Auto-generated OpenAPI 3.1 docs at `/v3/api-docs` |
| **JUnit 5 + Mockito** | Unit and integration testing |
| **Testcontainers** | Spin up real PostgreSQL and Redis in integration tests |
| **JaCoCo** | Code-coverage enforcement (90 % line + branch) |
| **Maven 3.9** | Build tool; multi-module ready |
| **Docker / Docker Compose** | Containerisation and local dev environment |
| **GitHub Actions** | CI pipeline |

---

## 2. Project Structure

```
user-auth-service/
├── .github/
│   └── workflows/
│       └── ci.yml                        # GitHub Actions pipeline
├── docker/
│   ├── Dockerfile                        # Production image definition
│   └── docker-compose.yml               # Local dev stack (app + postgres + redis)
├── docs/
│   └── decisions/                        # Architecture Decision Records (ADRs)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/company/auth/
│   │   │       ├── AuthServiceApplication.java          # @SpringBootApplication entry point
│   │   │       ├── config/
│   │   │       │   ├── SecurityConfig.java              # Spring Security filter chain
│   │   │       │   ├── AuthorizationServerConfig.java   # OAuth2 Authorization Server beans
│   │   │       │   ├── RedisConfig.java                 # RedisTemplate / connection factory
│   │   │       │   ├── MailConfig.java                  # JavaMailSender configuration
│   │   │       │   └── OpenApiConfig.java               # Springdoc / OpenAPI metadata
│   │   │       ├── domain/
│   │   │       │   ├── model/
│   │   │       │   │   ├── User.java                    # JPA entity — users table
│   │   │       │   │   ├── Role.java                    # JPA entity — roles table
│   │   │       │   │   ├── Permission.java              # JPA entity — permissions table
│   │   │       │   │   ├── PasswordResetToken.java      # JPA entity — password_reset_tokens
│   │   │       │   │   └── UserSession.java             # JPA entity — user_sessions
│   │   │       │   └── enums/
│   │   │       │       ├── RoleName.java                # Enum: ROLE_ADMIN, ROLE_USER, …
│   │   │       │       └── TokenStatus.java             # Enum: ACTIVE, EXPIRED, REVOKED
│   │   │       ├── repository/
│   │   │       │   ├── UserRepository.java              # Spring Data JPA
│   │   │       │   ├── RoleRepository.java
│   │   │       │   ├── PermissionRepository.java
│   │   │       │   ├── PasswordResetTokenRepository.java
│   │   │       │   └── UserSessionRepository.java
│   │   │       ├── service/
│   │   │       │   ├── AuthenticationService.java       # Login, token issuance orchestration
│   │   │       │   ├── UserService.java                 # User CRUD, role assignment
│   │   │       │   ├── SessionService.java              # Session lifecycle management
│   │   │       │   ├── PasswordRecoveryService.java     # Reset-token generation & validation
│   │   │       │   ├── TokenBlacklistService.java       # Redis-backed token revocation
│   │   │       │   └── RoleService.java                 # RBAC management
│   │   │       ├── web/
│   │   │       │   ├── controller/
│   │   │       │   │   ├── AuthController.java          # POST /auth/login, /auth/logout, /auth/refresh
│   │   │       │   │   ├── PasswordRecoveryController.java  # POST /auth/forgot-password, /auth/reset-password
│   │   │       │   │   ├── UserController.java          # CRUD /users — admin-only
│   │   │       │   │   └── RoleController.java          # CRUD /roles — admin-only
│   │   │       │   ├── dto/
│   │   │       │   │   ├── request/
│   │   │       │   │   │   ├── LoginRequest.java        # record — email + password
│   │   │       │   │   │   ├── RefreshTokenRequest.java # record
│   │   │       │   │   │   ├── ForgotPasswordRequest.java  # record
│   │   │       │   │   │   ├── ResetPasswordRequest.java   # record
│   │   │       │   │   │   └── CreateUserRequest.java   # record
│   │   │       │   │   └── response/
│   │   │       │   │       ├── TokenResponse.java       # record — access + refresh tokens
│   │   │       │   │       ├── UserResponse.java        # record — safe user projection
│   │   │       │   │       └── ErrorResponse.java       # record — RFC 7807 problem detail
│   │   │       │   ├── mapper/
│   │   │       │   │   ├── UserMapper.java              # MapStruct interface
│   │   │       │   │   └── RoleMapper.java
│   │   │       │   └── advice/
│   │   │       │       └── GlobalExceptionHandler.java  # @RestControllerAdvice — maps exceptions → HTTP
│   │   │       ├── security/
│   │   │       │   ├── CustomUserDetailsService.java    # Loads UserDetails from DB
│   │   │       │   ├── JwtTokenProvider.java            # JWT sign / verify (Spring Security OAuth2 resource server)
│   │   │       │   ├── JwtAuthenticationFilter.java     # OncePerRequestFilter — extracts Bearer token
│   │   │       │   └── PasswordEncoderBean.java         # BCryptPasswordEncoder @Bean
│   │   │       └── exception/
│   │   │           ├── AuthException.java               # Base runtime exception
│   │   │           ├── UserNotFoundException.java
│   │   │           ├── InvalidTokenException.java
│   │   │           ├── TokenExpiredException.java
│   │   │           └── RoleNotFoundException.java
│   │   └── resources/
│   │       ├── application.yml                          # Base config (references env vars)
│   │       ├── application-local.yml                   # Local dev overrides
│   │       ├── application-test.yml                    # Test profile overrides
│   │       └── db/migration/
│   │           ├── V1__create_users_roles_permissions.sql
│   │           ├── V2__create_sessions_table.sql
│   │           └── V3__create_password_reset_tokens.sql
│   └── test/
│       ├── java/
│       │   └── com/company/auth/
│       │       ├── service/
│       │       │   ├── AuthenticationServiceTest.java   # Pure unit tests — Mockito
│       │       │   ├── PasswordRecoveryServiceTest.java
│       │       │   ├── SessionServiceTest.java
│       │       │   └── TokenBlacklistServiceTest.java
│       │       ├── web/
│       │       │   ├── AuthControllerTest.java          # @WebMvcTest slice
│       │       │   └── PasswordRecoveryControllerTest.java
│       │       ├── repository/
│       │       │   └── UserRepositoryTest.java          # @DataJpaTest slice
│       │       ├── integration/
│       │       │   ├── AuthFlowIntegrationTest.java     # Full login → refresh → logout flow
│       │       │   └── PasswordRecoveryIntegrationTest.java
│       │       └── util/
│       │           ├── TestDataFactory.java             # Builder helpers for test fixtures
│       │           └── BaseIntegrationTest.java         # Testcontainers base class
│       └── resources/
│           └── application-test.yml                    # Testcontainers datasource overrides
├── tasks.md                                            # Agent-generated task breakdown (see §3)
├── pom.xml                                             # Root Maven POM
├── .gitignore
├── .editorconfig
└── README.md
```

---

## 3. Required Workflow

The agent **must** execute the following steps in order. Do not skip or reorder.

```
STEP 1 — READ SPECIFICATIONS
  - Read every file in docs/ and any linked story specs before touching code.
  - Identify all functional requirements, edge cases, and security constraints.

STEP 2 — CREATE tasks.md
  - Decompose the work into atomic, numbered tasks (e.g., T-01, T-02 …).
  - Each task must state: what to build, which files to create/modify, and acceptance criteria.
  - Commit tasks.md before writing any implementation code.

STEP 3 — SCAFFOLD STRUCTURE
  - Create every directory and placeholder file listed in §2.
  - Add the Maven pom.xml with all dependencies from §1 pinned to explicit versions.
  - Configure application.yml, application-test.yml, and Flyway migration scripts.

STEP 4 — IMPLEMENT DOMAIN & PERSISTENCE
  - Implement JPA entities → repositories → Flyway migrations in that order.
  - Every entity must have @CreatedDate / @LastModifiedDate via Spring Data Auditing.

STEP 5 — IMPLEMENT SECURITY LAYER
  - Configure SecurityConfig and AuthorizationServerConfig.
  - Implement CustomUserDetailsService, JwtTokenProvider, JwtAuthenticationFilter.
  - Implement TokenBlacklistService backed by Redis with TTL equal to JWT expiry.

STEP 6 — IMPLEMENT SERVICES & CONTROLLERS
  - Implement service classes; each public method must have a matching interface.
  - Implement controllers; validate all request bodies with Jakarta Bean Validation (@Valid).
  - Implement GlobalExceptionHandler mapping all custom exceptions to RFC 7807 responses.

STEP 7 — WRITE TESTS
  - Write unit tests first (service layer), then slice tests (web, repository), then integration tests.
  - All tests must pass before proceeding.
  - Run: mvn verify -Pcoverage and confirm JaCoCo thresholds are met (see §5).

STEP 8 — DOCKER & CI
  - Validate Dockerfile builds successfully: docker build -t user-auth-service .
  - Validate docker-compose up brings the full stack online.
  - Ensure ci.yml passes all jobs locally using act or by pushing to a feature branch.

STEP 9 — FINAL VALIDATION CHECKLIST
  □ mvn clean verify passes with zero test failures
  □ JaCoCo line coverage ≥ 90 %, branch coverage ≥ 90 %
  □ docker build produces an image under 400 MB
  □ No hardcoded secrets; all sensitive values read from environment variables
  □ OpenAPI docs reachable at http://localhost:8080/v3/api-docs when running locally
  □ All Flyway migrations apply cleanly on a fresh schema
  □ tasks.md is fully checked off
```

---

## 4. Coding Conventions

### 4.1 Naming

| Artifact | Convention | Example |
|---|---|---|
| Packages | `com.company.auth.<layer>` | `com.company.auth.service` |
| Classes | `PascalCase` + meaningful suffix | `AuthenticationService`, `LoginRequest` |
| Interfaces | No `I` prefix; use the noun | `UserRepository`, `TokenBlacklistService` |
| Methods | `camelCase`, verb-first | `issueAccessToken()`, `revokeSession()` |
| Constants | `UPPER_SNAKE_CASE` in a `final` class | `JwtConstants.TOKEN_PREFIX` |
| DTOs | Java `record` types | `record LoginRequest(String email, String password) {}` |
| Enums | `PascalCase` type, `UPPER_SNAKE_CASE` values | `RoleName.ROLE_ADMIN` |
| DB tables | `snake_case`, plural | `users`, `user_sessions`, `password_reset_tokens` |
| DB columns | `snake_case` | `created_at`, `is_active` |
| Flyway scripts | `V{n}__{description}.sql` | `V1__create_users_roles_permissions.sql` |

### 4.2 Architecture Patterns

- **Layered architecture**: `web → service → repository → domain`. No layer may skip a layer downward.
- **Service interfaces**: Every `*Service` class must implement a corresponding interface. Controllers depend only on interfaces.
- **DTO boundary**: Entities **never** leave the service layer. Controllers receive and return DTOs exclusively.
- **MapStruct** for all entity ↔ DTO conversions; no manual field-by-field mapping in service code.
- **No `@Autowired` on fields**: Use constructor injection only. Lombok `@RequiredArgsConstructor` is acceptable.
- **`@Transactional`**: Applied at the service method level, never at the repository level unless overriding default behaviour.
- **Pagination**: All list endpoints must accept `Pageable` and return `Page<T>`.

### 4.3 Security Patterns

- Passwords stored only as BCrypt hashes (cost factor ≥ 12). Plain-text passwords must never be logged.
- JWT access tokens: short-lived (15 min default, configurable via `app.security.jwt.access-token-expiry`).
- Refresh tokens: long-lived (7 days default), stored in Redis with TTL; rotated on every use.
- All endpoints require authentication except: `POST /auth/login`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `GET /actuator/health`.
- Method-level security enabled (`@EnableMethodSecurity`); use `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints.
- CSRF disabled for stateless REST; CORS configured explicitly — no wildcard origins in production.

### 4.4 Style

- Follow Google Java Style Guide.
- Maximum line length: 120 characters.
- All public classes, methods, and fields must have Javadoc.
- No `System.out.println`; use `@Slf4j` (Lombok) with `log.debug / info / warn / error`.
- `@Slf4j` log messages must use parameterised form: `log.info("User {} logged in", userId)` — never string concatenation.