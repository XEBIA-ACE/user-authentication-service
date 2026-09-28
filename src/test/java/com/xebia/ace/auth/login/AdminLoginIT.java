package com.xebia.ace.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xebia.ace.auth.login.domain.AdministratorUser;
import com.xebia.ace.auth.login.domain.AuthSession;
import com.xebia.ace.auth.login.domain.LoginAuditRecord;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import com.xebia.ace.auth.login.repository.AdminAuthAuditLogRepository;
import com.xebia.ace.auth.login.repository.AdministratorUserRepository;
import com.xebia.ace.auth.login.repository.AuthSessionRepository;
import com.xebia.ace.auth.login.security.PasswordHasher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminLoginIT {

    private static final String PASSWORD = "S3cure-Adm1n!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdministratorUserRepository userRepository;
    @Autowired
    private AuthSessionRepository sessionRepository;
    @Autowired
    private AdminAuthAuditLogRepository auditLogRepository;
    @Autowired
    private PasswordHasher passwordHasher;

    private AdministratorUser admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        admin = userRepository.save(new AdministratorUser(UUID.randomUUID(), "principal",
                "principal@school.example", passwordHasher.hash(PASSWORD)));
    }

    @Test
    void grantsAccessWithCorrectUsernameAndPersistsSession() throws Exception {
        JsonNode body = login("principal", PASSWORD, "10.1.2.3");

        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("errorCode").isNull()).isTrue();
        assertThat(body.get("user").get("id").asText()).isEqualTo(admin.getId().toString());
        assertThat(body.get("user").get("username").asText()).isEqualTo("principal");
        assertThat(body.get("user").get("email").asText()).isEqualTo("principal@school.example");
        assertThat(body.toString()).doesNotContain(PASSWORD).doesNotContain("$2a$");

        AuthSession session = sessionRepository.findById(UUID.fromString(body.get("sessionId").asText()))
                .orElseThrow();
        assertThat(session.getUserId()).isEqualTo(admin.getId());
        assertThat(session.getClientIp().getHostAddress()).isEqualTo("10.1.2.3");
        assertThat(session.getExpiresAt()).isAfter(session.getIssuedAt());
        assertThat(Instant.parse(body.get("expiresAt").asText())).isEqualTo(session.getExpiresAt());

        AdministratorUser reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(reloaded.getFailedLoginCount()).isZero();

        List<LoginAuditRecord> audit = auditLogRepository.findAllByLoginIdentifier("principal");
        assertThat(audit).singleElement().satisfies(record -> {
            assertThat(record.isSuccess()).isTrue();
            assertThat(record.getReasonCode()).isEqualTo("SUCCESS");
            assertThat(record.getUserId()).isEqualTo(admin.getId());
        });
    }

    @Test
    void grantsAccessWithEmailCaseInsensitively() throws Exception {
        JsonNode body = login("PRINCIPAL@School.Example", PASSWORD, null);

        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("user").get("id").asText()).isEqualTo(admin.getId().toString());
    }

    @Test
    void updatesLastLoginOnEachSuccessfulLogin() throws Exception {
        login("principal", PASSWORD, null);
        Instant first = userRepository.findById(admin.getId()).orElseThrow().getLastLoginAt();
        Thread.sleep(5);
        login("principal", PASSWORD, null);
        Instant second = userRepository.findById(admin.getId()).orElseThrow().getLastLoginAt();

        assertThat(second).isAfter(first);
        assertThat(sessionRepository.findByUserId(admin.getId())).hasSize(2);
    }

    @Test
    void showsIdenticalGenericErrorForAnyIncorrectCredentials() throws Exception {
        JsonNode wrongPassword = login("principal", "wrong-password", null);
        JsonNode unknownUser = login("nobody", PASSWORD, null);
        JsonNode blankIdentifier = login("", PASSWORD, null);
        JsonNode emptyPassword = login("principal", "", null);

        for (JsonNode body : List.of(wrongPassword, unknownUser, blankIdentifier, emptyPassword)) {
            assertThat(body.get("success").asBoolean()).isFalse();
            assertThat(body.get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(body.get("errorMessage").asText()).isEqualTo(LoginResultDto.GENERIC_LOGIN_FAILURE_MESSAGE);
            assertThat(body.get("sessionId").isNull()).isTrue();
            assertThat(body.get("user").isNull()).isTrue();
        }
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void incrementsFailedLoginCountOnWrongPasswordAndResetsOnSuccess() throws Exception {
        login("principal", "wrong-1", null);
        login("principal@school.example", "wrong-2", null);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getFailedLoginCount()).isEqualTo(2);

        login("principal", PASSWORD, null);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    void deniesInactiveAdministrator() throws Exception {
        admin.setActive(false);
        userRepository.save(admin);

        JsonNode body = login("principal", PASSWORD, null);

        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("errorMessage").asText()).isEqualTo(LoginResultDto.GENERIC_LOGIN_FAILURE_MESSAGE);
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void deniesLockedAdministrator() throws Exception {
        admin.setLocked(true);
        userRepository.save(admin);

        JsonNode body = login("principal", PASSWORD, null);

        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void auditsFailedAttemptForUnknownAccount() throws Exception {
        login("nobody", PASSWORD, "2001:db8::1");

        assertThat(auditLogRepository.findAllByLoginIdentifier("nobody")).singleElement()
                .satisfies(record -> {
                    assertThat(record.isSuccess()).isFalse();
                    assertThat(record.getUserId()).isNull();
                    assertThat(record.getReasonCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(record.getClientIp().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
                });
    }

    @Test
    void rejectsMissingPasswordWithBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"principal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    private JsonNode login(String usernameOrEmail, String password, String clientIp) throws Exception {
        var payload = objectMapper.createObjectNode()
                .put("usernameOrEmail", usernameOrEmail)
                .put("password", password)
                .put("clientIpAddress", clientIp);
        String response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
