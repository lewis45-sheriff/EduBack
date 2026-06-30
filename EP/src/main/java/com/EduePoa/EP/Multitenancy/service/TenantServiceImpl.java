package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.config.TenantConflictException;
import com.EduePoa.EP.Multitenancy.config.TenantNotFoundException;
import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;

    @Override
    @Transactional
    public Tenant create(TenantRegistrationRequest request) {
        // Validate tenant identifier uniqueness
        if (tenantRepository.existsByTenantIdentifier(request.getTenantIdentifier())) {
            throw new TenantConflictException(request.getTenantIdentifier());
        }

        Tenant tenant = getTenant(request);

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Tenant created: identifier={}, schoolName={}",
                savedTenant.getTenantIdentifier(), savedTenant.getSchoolName());

        // Provision default resources for the new tenant
        provisioningService.provisionTenant(savedTenant);

        return savedTenant;
    }

    @NotNull
    private static Tenant getTenant(TenantRegistrationRequest request) {
        Tenant tenant = new Tenant();
        tenant.setTenantIdentifier(request.getTenantIdentifier());
        tenant.setSchoolName(request.getSchoolName());
        tenant.setPhysicalAddress(request.getPhysicalAddress());
        tenant.setEmailDomain(request.getEmailDomain());
        tenant.setPhoneNumber(request.getPhoneNumber());
        tenant.setLogoUrl(request.getLogoUrl());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setSubscriptionPlan(
                request.getSubscriptionPlan() != null ? request.getSubscriptionPlan() : "BASIC"
        );
        return tenant;
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant findById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found with ID: " + id, String.valueOf(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant findByIdentifier(String identifier) {
        return tenantRepository.findByTenantIdentifier(identifier)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found with identifier: " + identifier, identifier));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdentifier(String identifier) {
        return tenantRepository.existsByTenantIdentifier(identifier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    @Override
    @Transactional
    public Tenant update(Long id, TenantRegistrationRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found with ID: " + id, String.valueOf(id)));

        // Update mutable fields (tenantIdentifier is immutable)
        tenant.setSchoolName(request.getSchoolName());
        tenant.setPhysicalAddress(request.getPhysicalAddress());
        tenant.setEmailDomain(request.getEmailDomain());
        tenant.setPhoneNumber(request.getPhoneNumber());
        tenant.setLogoUrl(request.getLogoUrl());
        if (request.getSubscriptionPlan() != null) {
            tenant.setSubscriptionPlan(request.getSubscriptionPlan());
        }

        Tenant updatedTenant = tenantRepository.save(tenant);
        log.info("Tenant updated: identifier={}, schoolName={}",
                updatedTenant.getTenantIdentifier(), updatedTenant.getSchoolName());

        return updatedTenant;
    }
}
