## S-002

### Purpose
Define functional behavior for secure administrator login via the User Authentication Service to grant or deny access based on credential validity.

### Scope
Covers administrator interactive login, credential validation, access grant/deny behavior, error messaging, and related audit aspects for the User Authentication Service only.

### Non-Goals
1. Password reset and recovery flows  
2. New user registration and provisioning  
3. Multi-factor authentication behavior  
4. Session refresh and logout behavior  
5. Role-based authorization within downstream systems  
6. Password strength and rotation policies  
7. Account lifecycle management (create/disable/delete)  
8. Localization of messages beyond English  
9. Client UI layout and visual styling  
10. Rate limiting and intrusion detection algorithms  

### Key Entities
AdministratorUser:  
- id: string  
- username: string  
- email: string  
- isActive: boolean  
- isLocked: boolean  
- failedLoginCount: integer  
- lastLoginAt: datetime  

CredentialSubmission:  
- usernameOrEmail: string  
- password: string  
- clientDeviceId: string [NEEDS CLARIFICATION: Is client device identifier required?]  
- clientIpAddress: string  

AuthSession:  
- sessionId: string  
- userId: string  
- issuedAt: datetime  
- expiresAt: datetime  

LoginResult:  
- success: boolean  
- errorCode: string (nullable)  
- errorMessage: string (nullable)  
- requiresAdditionalAction: boolean  

Relationship:  
- AdministratorUser 1..1 – 0..* AuthSession  
- AdministratorUser 1..1 – 0..* CredentialSubmission (logical audit trail)  

### Functional Requirements
FR-001: User Authentication Service MUST authenticate CredentialSubmission against stored administrator credentials.  
FR-002: User Authentication Service MUST grant system access when credentials match an active AdministratorUser.  
FR-003: User Authentication Service MUST deny system access when credentials are invalid.  
FR-004: User Authentication Service MUST return a non-sensitive error message on failed login.  
FR-005: User Authentication Service MUST NOT disclose whether username or password was incorrect.  
FR-006: User Authentication Service SHOULD treat username and email as alternative login identifiers.  
FR-007: User Authentication Service MUST validate that AdministratorUser isActive before granting access.  
FR-008: User Authentication Service MUST handle non-existent accounts as invalid credentials.  
FR-009: User Authentication Service SHOULD record a successful login timestamp for AdministratorUser.  
FR-010: User Authentication Service SHOULD increment failedLoginCount on failed attempts.  
FR-011: User Authentication Service MAY include a generic reason code in LoginResult for client behavior.  
FR-012: User Authentication Service MUST establish an AuthSession on successful login.  
FR-013: User Authentication Service MUST ensure AuthSession belongs to the authenticated AdministratorUser.  
FR-014: User Authentication Service SHOULD validate basic CredentialSubmission format before authentication.  
FR-015: User Authentication Service MUST prevent login for locked AdministratorUser accounts.  
FR-016: User Authentication Service SHOULD make error messages consistent across failure modes.  
FR-017: User Authentication Service MUST operate identically for web and mobile clients.  
FR-018: User Authentication Service SHOULD provide response data needed by downstream Ticket Management System to identify the user.  
FR-019: User Authentication Service MUST NOT expose password or password-derived data in any response.  
FR-020: User Authentication Service MAY log authentication outcomes for audit and security analysis.  

### Edge Cases
EC-001 (FR-001, FR-003, P2):  
Given an administrator submits a blank usernameOrEmail and non-blank password,  
When the login is processed,  
Then access is denied and a generic invalid-credentials error is returned.

EC-002 (FR-001, FR-003, FR-005, P2):  
Given an administrator submits a valid username with an empty password,  
When the login is processed,  
Then access is denied with the same generic error as any other failure.

EC-003 (FR-001, FR-003, FR-008, P2):  
Given a usernameOrEmail that does not match any AdministratorUser,  
When the login is processed,  
Then the system behaves as for invalid credentials without indicating account absence.

EC-004 (FR-001, FR-003, FR-007, P2):  
Given an administrator account that is inactive,  
When correct credentials are submitted,  
Then access is denied and a generic unauthorized error is returned without exposing status details.

EC-005 (FR-001, FR-003, FR-015, P2):  
Given an administrator account that is locked due to policy,  
When correct credentials are submitted,  
Then access is denied and a non-specific error is returned.

EC-006 (FR-004, FR-016, P2):  
Given repeated failed login attempts with differing mistakes,  
When each attempt fails,  
Then the error message text remains identical across attempts.

EC-007 (FR-001, FR-002, FR-012, P1):  
Given an active, unlocked administrator with correct credentials,  
When they submit login,  
Then access is granted and a valid AuthSession is established.

EC-008 (FR-001, FR-002, FR-009, P2):  
Given an active administrator logging in successfully after prior success,  
When login completes,  
Then lastLoginAt is updated to the most recent successful login time.

### Assumptions
A-001: Only administrators are allowed to log in via this flow. (Affects FR-001, FR-002, FR-007, FR-015, EC-007)  
A-002: Error messages must not reveal which field caused failure. (Affects FR-004, FR-005,