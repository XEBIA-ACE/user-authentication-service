# user-authentication-service

User Authentication Service (S-002). Currently provides secure administrator login (US-001).

## Stack

Java 21, Spring Boot 3.3, Spring Data JPA, Flyway, PostgreSQL, BCrypt (spring-security-crypto).

## Running locally

```bash
docker run -d --name authdb -e POSTGRES_DB=authdb -e POSTGRES_USER=auth -e POSTGRES_PASSWORD=auth -p 5432:5432 postgres:16-alpine
AUTH_DB_PASSWORD=auth mvn spring-boot:run
```

Flyway applies the schema from `src/main/resources/db/migration` on startup.

| Variable | Default | Description |
|---|---|---|
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/authdb` | JDBC URL of the auth database |
| `AUTH_DB_USERNAME` | `auth` | Database user |
| `AUTH_DB_PASSWORD` | _(empty)_ | Database password |
| `AUTH_SESSION_TTL` | `PT30M` | Lifetime of an issued session (ISO-8601 duration) |
| `PORT` | `8080` | HTTP port |

## API

### `POST /auth/login`

Request:

```json
{ "usernameOrEmail": "principal", "password": "…", "clientDeviceId": "optional", "clientIpAddress": "optional" }
```

`usernameOrEmail` matches either the username or the email, case-insensitively. If `clientIpAddress` is omitted it is
taken from the first `X-Forwarded-For` entry, falling back to the remote address.

Responses (`200 OK` whenever the request was processed; `success` indicates grant or deny):

```json
{ "success": true, "errorCode": null, "errorMessage": null, "requiresAdditionalAction": false,
  "sessionId": "5f0c…", "user": { "id": "…", "username": "principal", "email": "principal@school.example" },
  "issuedAt": "2026-01-01T10:00:00Z", "expiresAt": "2026-01-01T10:30:00Z" }
```

```json
{ "success": false, "errorCode": "INVALID_CREDENTIALS",
  "errorMessage": "Login failed. Please check your credentials and try again.",
  "requiresAdditionalAction": false, "sessionId": null, "user": null, "issuedAt": null, "expiresAt": null }
```

| `errorCode` | When |
|---|---|
| `INVALID_CREDENTIALS` | Unknown account, wrong password, blank or over-long fields |
| `UNAUTHORIZED` | Correct password but the account is inactive or locked |
| `INVALID_REQUEST` | `400` — malformed JSON or missing `usernameOrEmail` / `password` |
| `INTERNAL_ERROR` | `500` — unexpected server error |

The error message is identical for every failure so the response never reveals which check failed. Every attempt is
recorded in `admin_auth_audit_log`.

## Tests

```bash
mvn verify   # unit tests + Testcontainers-backed integration tests (requires Docker)
```
