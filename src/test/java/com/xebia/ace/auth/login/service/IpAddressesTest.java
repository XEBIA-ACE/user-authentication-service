package com.xebia.ace.auth.login.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "255.255.255.255", "::1", "2001:db8::1", " 192.168.1.10 "})
    void parsesIpLiterals(String value) {
        assertThat(IpAddresses.parseLiteral(value)).isPresent();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "localhost", "example.com", "256.1.1.1", "1.2.3", "abc"})
    void rejectsNonLiterals(String value) {
        assertThat(IpAddresses.parseLiteral(value)).isEmpty();
    }
}
