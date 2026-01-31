package com.EduePoa.EP.Authentication.Role.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private String name;
    private String permission;
    private String description;
}
