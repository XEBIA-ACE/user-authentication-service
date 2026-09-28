package com.xebia.ace.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.session")
public record AuthSessionProperties(@DefaultValue("PT30M") Duration ttl) {
}
