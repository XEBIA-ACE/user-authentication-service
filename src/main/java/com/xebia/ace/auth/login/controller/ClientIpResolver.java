package com.xebia.ace.auth.login.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public String resolve(String submittedClientIp, HttpServletRequest request) {
        if (StringUtils.hasText(submittedClientIp)) {
            return submittedClientIp.strip();
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
