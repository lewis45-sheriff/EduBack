package com.EduePoa.EP.Authentication.Auth.Request;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
    /**
     * Optional tenant identifier for multi-tenant login.
     * If provided, the system validates that the user belongs to this tenant.
     * If not provided, the user's stored tenant_id is used directly.
     */
    private String tenantId;
}
