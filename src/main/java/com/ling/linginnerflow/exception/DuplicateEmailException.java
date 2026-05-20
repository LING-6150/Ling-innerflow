package com.ling.linginnerflow.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException(String email) {
        super(HttpStatus.CONFLICT, "DUPLICATE_EMAIL",
                "Email already registered: " + email);
    }
}
