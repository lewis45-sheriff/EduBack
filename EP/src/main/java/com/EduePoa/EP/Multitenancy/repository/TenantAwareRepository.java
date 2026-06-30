package com.EduePoa.EP.Multitenancy.repository;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantScopedEntity, ID> extends JpaRepository<T, ID> {
}
