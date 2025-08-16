package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.Enum.Status;
import jakarta.persistence.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.sql.Timestamp;  // ✅ CORRECT import (not java.security.Timestamp)


@Data
@RequiredArgsConstructor
@Entity
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, name = "name")
    private String name;

    @CreationTimestamp
    @Column(name = "created_on")
    private Timestamp createdOn;

    @UpdateTimestamp
    @Column(name = "updated_on")
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp updatedOn;

    @Column(name = "enabled_flag")
    private char enabledFlag = 'Y';  // Default to 'Y' for active

    @Column(name = "deleted_flag")
    private char deletedFlag = 'N';  // Default to 'N' for not deleted

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;
}