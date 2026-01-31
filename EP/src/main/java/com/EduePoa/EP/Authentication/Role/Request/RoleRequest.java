package com.EduePoa.EP.Authentication.Role.Request;

import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {
    private String name;
    private List<String> permissions;
}
@Data
class PermissionInput {
    private String name;
    private String permission;
    private String description;
}
