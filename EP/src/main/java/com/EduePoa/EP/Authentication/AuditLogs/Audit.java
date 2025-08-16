package com.EduePoa.EP.Authentication.AuditLogs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Audit {
    @Id
    @GeneratedValue
    @JsonIgnore
    private UUID sn;
    private Timestamp timestamp  = new Timestamp(System.currentTimeMillis());
    private String device;
    private String module;
    private String ipAddress;
    @Column(length = 40000) // or bigger if needed
    private String activity;
    private String userEmail;
}
