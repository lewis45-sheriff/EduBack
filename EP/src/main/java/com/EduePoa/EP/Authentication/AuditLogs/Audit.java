package com.EduePoa.EP.Authentication.AuditLogs;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Audit extends TenantScopedEntity {
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
