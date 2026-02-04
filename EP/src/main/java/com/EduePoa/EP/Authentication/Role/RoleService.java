package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Request.RoleRequest;
import com.EduePoa.EP.Authentication.Role.Response.PermissionDTO;
import com.EduePoa.EP.Authentication.Role.Response.RoleResponse;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditService auditService;

    @Transactional // Add this annotation
    public void createRole(@NonNull String name) {
        try {
            Role role = new Role();
            role.setName(name);
            role.setEnabledFlag('Y');
            role.setDeletedFlag('N'); //  Add this too
            role.setStatus(Status.ACTIVE);

            //  Initialize the collection
            role.setRolePermissions(new HashSet<>());

            //  Add all permissions to the role BEFORE saving
            for (Permissions permission : Permissions.values()) {
                RolePermission rolePermission = new RolePermission(role, permission);
                role.getRolePermissions().add(rolePermission);
            }

            role = roleRepository.save(role);



            log.info("Role created: {} with {} permissions", role.getName(), role.getRolePermissions().size());
        } catch (DataIntegrityViolationException e) {
            log.error("Database constraint error while creating role: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error occurred while creating role: {}", e.getMessage());
            e.printStackTrace(); //  Add this to see the full error
        }
    }
    @Transactional
    public void updateRolePermissions(Role role) {
        try {
            role.getRolePermissions().clear();
            roleRepository.save(role);

            role.setRolePermissions(new HashSet<>());

            for (Permissions permission : Permissions.values()) {
                RolePermission rolePermission = new RolePermission(role, permission);
                role.getRolePermissions().add(rolePermission);
            }

            roleRepository.save(role);

            log.info("Updated role: {} with {} permissions", role.getName(), role.getRolePermissions().size());
        } catch (Exception e) {
            log.error("Error updating role permissions: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    public CustomResponse<RoleResponse> newRole(RoleRequest roleRequest) {
        CustomResponse<RoleResponse> response = new CustomResponse<>();

        try {
            // Check if role already exists
            Optional<Role> existingRole = roleRepository.findByName(roleRequest.getName());
            if (existingRole.isPresent()) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("Role with the same name already exists");
                response.setEntity(null);
                auditService.log("ROLE_MANAGEMENT", "Attempted to create duplicate role:", roleRequest.getName());
                return response;
            }

            // Create new role
            Role role = new Role();
            role.setName(roleRequest.getName());
            role.setStatus(Status.ACTIVE);
            role.setEnabledFlag('Y');
            role.setDeletedFlag('N');

            // Add permissions
            if (roleRequest.getPermissions() != null && !roleRequest.getPermissions().isEmpty()) {
                for (String permissionStr : roleRequest.getPermissions()) {
                    try {
                        Permissions permission = Permissions.fromString(permissionStr);
                        role.addPermission(permission);
                    } catch (IllegalArgumentException e) {
                        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                        response.setMessage("Invalid permission: " + permissionStr);
                        response.setEntity(null);
                        return response;
                    }
                }
            }

            Role savedRole = roleRepository.save(role);

            // Map to RoleResponse DTO
            RoleResponse roleResponse = new RoleResponse();
            roleResponse.setId(savedRole.getId());
            roleResponse.setName(savedRole.getName());
            roleResponse.setEnabledFlag(savedRole.getEnabledFlag());
            roleResponse.setDeletedFlag(savedRole.getDeletedFlag());
            roleResponse.setStatus(savedRole.getStatus());

            // Map permissions
            List<PermissionDTO> permissionDTOs = savedRole.getRolePermissions().stream()
                    .map(rp -> {
                        PermissionDTO dto = new PermissionDTO();
                        dto.setName(rp.getPermission().name());
                        dto.setPermission(rp.getPermission().getPermission());
                        dto.setDescription(rp.getPermission().getDescription());
                        return dto;
                    })
                    .collect(Collectors.toList());
            roleResponse.setPermissions(permissionDTOs);

            response.setEntity(roleResponse);
            response.setMessage("Role created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());

            auditService.log("ROLE_MANAGEMENT", "Created new role:", savedRole.getName(),
                    "with ID:", String.valueOf(savedRole.getId()),
                    "and", String.valueOf(permissionDTOs.size()), "permissions");

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage("Error creating role: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            auditService.log("ROLE_MANAGEMENT", "Error creating role:", roleRequest.getName(), e.getMessage());
        }

        return response;
    }

    public CustomResponse<List<RoleResponse>> getAllRoles() {
        CustomResponse<List<RoleResponse>> response = new CustomResponse<>();
        try {
            List<Role> roles = roleRepository.findAll();

            List<RoleResponse> roleResponses = roles.stream()
                    .map(role -> {
                        RoleResponse dto = new RoleResponse();
                        dto.setId(role.getId());
                        dto.setName(role.getName());
                        dto.setCreatedOn(role.getCreatedOn());
                        dto.setUpdatedOn(role.getUpdatedOn());
                        dto.setEnabledFlag(role.getEnabledFlag());
                        dto.setDeletedFlag(role.getDeletedFlag());
                        dto.setStatus(role.getStatus());

                        // Map permissions
                        List<PermissionDTO> permissionDTOs = role.getRolePermissions().stream()
                                .map(rp -> {
                                    PermissionDTO permDto = new PermissionDTO();
                                    permDto.setName(rp.getPermission().name());
                                    permDto.setPermission(rp.getPermission().getPermission());
                                    permDto.setDescription(rp.getPermission().getDescription());
                                    return permDto;
                                })
                                .collect(Collectors.toList());
                        dto.setPermissions(permissionDTOs);

                        return dto;
                    })
                    .collect(Collectors.toList());

            response.setEntity(roleResponses);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(
                    roleResponses.isEmpty()
                            ? "No roles found."
                            : "Roles retrieved successfully."
            );

            // Log the operation
            auditService.log("ROLE_MANAGEMENT", "Retrieved", String.valueOf(roleResponses.size()), "roles");

        } catch (RuntimeException e) {
            response.setEntity(Collections.emptyList());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching roles: " + e.getMessage());

            // Log the error
            auditService.log("ROLE_MANAGEMENT", "Error fetching roles:", e.getMessage());
        }
        return response;
    }
    CustomResponse<?> getAllPermissions(){
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            List<Map<String, String>> permissionsList = new ArrayList<>();

            for (Permissions permission : Permissions.values()) {
                Map<String, String> permissionMap = new HashMap<>();
                permissionMap.put("name", permission.name());
                permissionMap.put("permission", permission.getPermission());
                permissionMap.put("description", permission.getDescription());
                permissionsList.add(permissionMap);
            }

            response.setEntity(permissionsList);
            response.setMessage("Permissions retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (Exception e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }



}
