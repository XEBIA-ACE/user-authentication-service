package com.xebia.ace.auth.login.controller;

import com.xebia.ace.auth.login.dto.LoginRequestDto;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import com.xebia.ace.auth.login.service.AdminAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminAuthenticationService authenticationService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AdminAuthenticationService authenticationService, ClientIpResolver clientIpResolver) {
        this.authenticationService = authenticationService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResultDto> login(@Valid @RequestBody LoginRequestDto request,
                                                HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(request.clientIpAddress(), httpRequest);
        LoginResultDto result = authenticationService.login(request.withClientIpAddress(clientIp));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
