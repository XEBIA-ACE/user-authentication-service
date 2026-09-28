package com.xebia.ace.auth.login.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotNull String usernameOrEmail,
        @NotNull String password,
        @Size(max = 255) String clientDeviceId,
        @Size(max = 45) String clientIpAddress) {

    public LoginRequestDto withClientIpAddress(String resolvedClientIpAddress) {
        return new LoginRequestDto(usernameOrEmail, password, clientDeviceId, resolvedClientIpAddress);
    }

    @Override
    public String toString() {
        return "LoginRequestDto[usernameOrEmail=" + usernameOrEmail + ", password=***, clientDeviceId="
                + clientDeviceId + ", clientIpAddress=" + clientIpAddress + "]";
    }
}
