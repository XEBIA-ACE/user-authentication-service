package com.xebia.ace.auth.login.security;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    /** BCrypt only considers the first 72 bytes of input; longer passwords are rejected rather than truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        if (exceedsMaxLength(rawPassword)) {
            throw new IllegalArgumentException("Password exceeds " + MAX_PASSWORD_BYTES + " bytes");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || exceedsMaxLength(rawPassword)) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    private static boolean exceedsMaxLength(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
