package com.EduePoa.EP.Authentication.User.Response;

import com.EduePoa.EP.Authentication.Enum.Status;
import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String userName;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String gender;
    private String location;
    private String deletedFlag;
    private String enabledFlag;
    private Status status;
    private String createdOn;
    private String updatedOn;
}
