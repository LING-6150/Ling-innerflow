package com.ling.linginnerflow.exception;

import org.springframework.http.HttpStatus;

public class DuplicateUsernameException extends BusinessException {
    public DuplicateUsernameException(String username) {
        super(HttpStatus.CONFLICT, "DUPLICATE_USERNAME",
                "Username already taken: " + username);
    }
}
