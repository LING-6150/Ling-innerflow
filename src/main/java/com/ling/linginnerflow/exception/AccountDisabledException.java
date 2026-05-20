package com.ling.linginnerflow.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends BusinessException {
    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account has been disabled");
    }
}
