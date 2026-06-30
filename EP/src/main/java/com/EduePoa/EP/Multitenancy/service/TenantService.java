package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.entity.Tenant;

import java.util.List;

public interface TenantService {

    Tenant create(TenantRegistrationRequest request);
    Tenant findById(Long id);
    Tenant findByIdentifier(String identifier);
    boolean existsByIdentifier(String identifier);
    List<Tenant> findAll();
    Tenant update(Long id, TenantRegistrationRequest request);
}
