package com.xebia.ace.auth.login.service;

import com.xebia.ace.auth.login.dto.LoginRequestDto;
import com.xebia.ace.auth.login.dto.LoginResultDto;

public interface AdminAuthenticationService {

    LoginResultDto login(LoginRequestDto request);
}
