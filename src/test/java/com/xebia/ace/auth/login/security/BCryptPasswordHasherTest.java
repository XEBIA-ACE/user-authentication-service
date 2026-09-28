package com.xebia.ace.auth.login.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void matchesOnlyTheOriginalPassword() {
        String hash = hasher.hash("S3cure-Adm1n!");

        assertThat(hash).doesNotContain("S3cure-Adm1n!");
        assertThat(hasher.matches("S3cure-Adm1n!", hash)).isTrue();
        assertThat(hasher.matches("s3cure-adm1n!", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches("S3cure-Adm1n!", null)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        String base = "a".repeat(BCryptPasswordHasher.MAX_PASSWORD_BYTES);
        String hash = hasher.hash(base);

        assertThat(hasher.matches(base + "extra", hash)).isFalse();
        assertThatThrownBy(() -> hasher.hash(base + "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
