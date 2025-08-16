package com.EduePoa.EP.Authentication.Role.Response;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.sql.Timestamp;  // ✅ CORRECT import (not java.security.Timestamp)

@Data
public class RoleResponse {
    private Long id;
    private String name;

    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp createdOn;

    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp updatedOn;

    private char enabledFlag;
    private char deletedFlag;
    private Status status;
}
