package com.EduePoa.EP.Authentication.Role.Request;

import lombok.Data;

import java.util.List;

@Data
public class RoleEditRequest {
    private Long id;
    private String name;
    private List<String> permissions;
}


