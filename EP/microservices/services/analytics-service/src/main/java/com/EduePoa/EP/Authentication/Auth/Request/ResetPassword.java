package com.EduePoa.EP.Authentication.Auth.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPassword {
    private String email;
    private String oldPassword;
    private String newPassword;
}