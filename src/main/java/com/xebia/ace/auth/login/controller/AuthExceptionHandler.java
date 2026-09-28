package com.xebia.ace.auth.login.controller;

import com.xebia.ace.auth.login.dto.LoginErrorCode;
import com.xebia.ace.auth.login.dto.LoginResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    static final String INVALID_REQUEST_MESSAGE = "The login request is malformed or missing required fields.";
    static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred. Please try again later.";

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<LoginResultDto> handleInvalidRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(LoginResultDto.failure(LoginErrorCode.INVALID_REQUEST, INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LoginResultDto> handleUnexpected(Exception ex) {
        log.error("Unexpected error while processing login", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LoginResultDto.failure(LoginErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE));
    }
}
