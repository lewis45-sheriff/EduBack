package com.EduePoa.EP.Authentication.Auth.Response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private Long id;
    private String username;
    private String email;
    private String firstname;
    private String lastname;
    private String accessToken;
    private long tokenExpiresIn;
    private String refreshToken;
    private long refreshTokenExpiresIn;
    private String phoneNumber;
    private String role;


}
