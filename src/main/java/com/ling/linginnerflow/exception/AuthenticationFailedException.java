package com.ling.linginnerflow.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationFailedException extends BusinessException {
    public AuthenticationFailedException() {
        super(HttpStatus.UNAUTHORIZED, "AUTH_FAILED", "Invalid email or password");
    }
}
