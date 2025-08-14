package com.EduePoa.EP.Authentication.Role;

import com.EduePoa.EP.Authentication.Enum.Status;
import jakarta.persistence.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonFormat;


import java.sql.Timestamp;
@Data
@RequiredArgsConstructor
@Entity
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true , name = "name")
    private String name;

    @CreationTimestamp
    private Timestamp createdOn;

    @UpdateTimestamp
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    private Timestamp updatedOn;

    private char enabledFlag = 'N';
    private char deletedFlag = 'Y';

    @Enumerated(EnumType.STRING)
    private Status status;
}
