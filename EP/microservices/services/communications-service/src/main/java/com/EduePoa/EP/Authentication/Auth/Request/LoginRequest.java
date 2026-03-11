package com.EduePoa.EP.Authentication.Auth.Request;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
