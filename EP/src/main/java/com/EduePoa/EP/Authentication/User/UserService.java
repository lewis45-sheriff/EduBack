package com.EduePoa.EP.Authentication.User;

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

import java.util.Optional;

@Service
@AllArgsConstructor

public class UserService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;



    CustomResponse<?> create(UserRequest userRequest){
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


            Role role = roleRepository.findById(userRequest.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Role not found with ID: " + userRequest.getRoleId()
                    ));

            user.setRole(role);
            User savedUser = userRepository.save(user);
            UserResponse userResponse = getUserResponse(savedUser);

            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(userResponse);
            response.setMessage("User Successfully Created ");









        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
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
        return userResponse;
    }
}
