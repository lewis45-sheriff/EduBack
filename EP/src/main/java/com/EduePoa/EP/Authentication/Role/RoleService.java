package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Request.RoleRequest;
import com.EduePoa.EP.Authentication.Role.Response.RoleResponse;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditService auditService;


    public void createRole(@NonNull String name) {
        try {
            Role role = new Role();
            role.setName(name);
            role.setEnabledFlag('Y');
            role.setStatus(Status.ACTIVE);
            role = roleRepository.save(role);

            log.info("Role created: {}", role);
        } catch (DataIntegrityViolationException e) {
            log.error("Database constraint error while creating role: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error occurred while creating role: {}", e.getMessage());
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

                // Log duplicate role attempt
                auditService.log("ROLE_MANAGEMENT", "Attempted to create duplicate role:", roleRequest.getName());

                return response;
            }

            // Create new role
            Role role = new Role();
            role.setName(roleRequest.getName());
            role.setStatus(Status.ACTIVE);
            role.setEnabledFlag('Y');
            role.setDeletedFlag('N');

            Role savedRole = roleRepository.save(role);

            // Map to RoleResponse DTO
            RoleResponse roleResponse = new RoleResponse();
            roleResponse.setId(savedRole.getId());
            roleResponse.setName(savedRole.getName());
            roleResponse.setEnabledFlag(savedRole.getEnabledFlag());
            roleResponse.setDeletedFlag(savedRole.getDeletedFlag());
            roleResponse.setStatus(savedRole.getStatus());

            response.setEntity(roleResponse);
            response.setMessage("Role created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());

            // Log successful role creation
            auditService.log("ROLE_MANAGEMENT", "Created new role:", savedRole.getName(), "with ID:", String.valueOf(savedRole.getId()));

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage("Error creating role: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

            // Log the error
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
        } catch (RuntimeException e) {
            response.setEntity(Collections.emptyList());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching roles: " + e.getMessage());
        }
        return response;
    }



}
