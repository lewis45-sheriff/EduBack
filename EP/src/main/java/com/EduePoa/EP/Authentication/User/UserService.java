package com.EduePoa.EP.Authentication.User;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.Request.UserRequest;
import com.EduePoa.EP.Authentication.User.Response.UserResponse;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.Utils.ResourceNotFoundException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor

public class UserService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private final PasswordEncoder passwordEncoder;



    public CustomResponse<?> create(UserRequest userRequest) {
        CustomResponse<UserResponse> response = new CustomResponse<>();
        try {
            User user = new User();
            user.setFirstName(userRequest.getFirstName());
            user.setLastName(userRequest.getLastName());
            user.setEmail(userRequest.getEmail());

            String rawPassword = "1234";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            user.setPassword(encodedPassword);
            user.setStatus(Status.ACTIVE);
            user.setPasswordReset(true);

            Role role = roleRepository.findById(userRequest.getRoleId())
                    .orElseThrow(() -> {
                        auditService.log("USER_MANAGEMENT", "Attempted to create user with missing role ID:", String.valueOf(userRequest.getRoleId()));
                        return new ResourceNotFoundException(
                                "Role not found with ID: " + userRequest.getRoleId()
                        );
                    });

            user.setRole(role);
            User savedUser = userRepository.save(user);
            UserResponse userResponse = getUserResponse(savedUser);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(userResponse);
            response.setMessage("User Successfully Created");

            // Audit log for success
            auditService.log("USER_MANAGEMENT",
                    "Created new user:",
                    savedUser.getFirstName() + " " + savedUser.getLastName(),
                    "Email:", savedUser.getEmail(),
                    "Role:", savedUser.getRole().getName(),
                    "ID:", String.valueOf(savedUser.getId())
            );

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

            // Audit log for error
            auditService.log("USER_MANAGEMENT",
                    "Error creating user:",
                    userRequest.getFirstName() + " " + userRequest.getLastName(),
                    "Email:", userRequest.getEmail(),
                    e.getMessage()
            );
        }
        return response;
    }

    public CustomResponse<List<UserResponse>> getAllUsers() {
        CustomResponse<List<UserResponse>> response = new CustomResponse<>();
        try {
            List<User> users = userRepository.findAll();

            List<UserResponse> userResponses = users.stream()
                    .map(UserService::getUserResponse)
                    .collect(Collectors.toList());

            response.setEntity(userResponses);
            response.setMessage("Users retrieved successfully.");
            response.setStatusCode(HttpStatus.OK.value());
        } catch (RuntimeException e) {
            response.setEntity(Collections.emptyList());
            response.setMessage("Error retrieving users: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }



    private static UserResponse getUserResponse(User savedUser) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(savedUser.getId());
        userResponse.setFirstName(savedUser.getFirstName());
        userResponse.setEmail(savedUser.getEmail());
        userResponse.setCreatedOn(String.valueOf(savedUser.getCreatedOn()));
        userResponse.setDeletedFlag(String.valueOf(savedUser.getDeletedFlag()));
        userResponse.setUserName(savedUser.getUsername());
        userResponse.setEnabledFlag(String.valueOf(savedUser.getEnabledFlag()));
        userResponse.setUpdatedOn(String.valueOf(savedUser.getUpdatedOn()));
        userResponse.setRole(savedUser.getRole().getName());
        userResponse.setStatus(savedUser.getStatus());
        userResponse.setLastName(savedUser.getLastName());
        return userResponse;
    }
}
