package com.ling.linginnerflow.user;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}