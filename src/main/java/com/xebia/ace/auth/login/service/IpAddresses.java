package com.xebia.ace.auth.login.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses IP address literals without ever triggering a DNS lookup. */
final class IpAddresses {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpAddresses() {
    }

    static Optional<InetAddress> parseLiteral(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String candidate = value.strip();
        boolean literal = IPV4.matcher(candidate).matches()
                || (candidate.indexOf(':') >= 0 && IPV6.matcher(candidate).matches());
        if (!literal) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
