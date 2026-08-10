package com.EduePoa.EP.Multitenancy.base;

import com.EduePoa.EP.Multitenancy.listener.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base mapped superclass for all tenant-scoped entities.
 * <p>
 * Provides automatic Hibernate filtering by tenant_id and JPA lifecycle
 * validation via {@link TenantEntityListener}. All entities that belong
 * to a specific tenant should extend this class.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
public abstract class TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;
}
