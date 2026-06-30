package com.EduePoa.EP.Multitenancy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_audit_logs")
public class TenantAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantIdentifier;

    @Column(nullable = false)
    private String action; // CREATED, SUSPENDED, REACTIVATED, DECOMMISSIONED

    @Column(nullable = false)
    private String actor; // username of Platform_Admin

    private String reason;

    @CreationTimestamp
    private Timestamp timestamp;
}
