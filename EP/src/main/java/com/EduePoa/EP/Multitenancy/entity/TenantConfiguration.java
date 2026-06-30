package com.EduePoa.EP.Multitenancy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "tenant_configurations", uniqueConstraints = {
        @UniqueConstraint(name = "idx_tenant_config", columnNames = {"tenant_id", "config_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantIdentifier;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "is_sensitive")
    private Boolean isSensitive;
}
