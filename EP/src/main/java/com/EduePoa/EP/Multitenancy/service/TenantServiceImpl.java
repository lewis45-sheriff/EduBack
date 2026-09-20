package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.FileStorage.FileStorageService;
import com.EduePoa.EP.Multitenancy.config.TenantConflictException;
import com.EduePoa.EP.Multitenancy.config.TenantNotFoundException;
import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final FileStorageService fileStorageService;

    private static final String LOGO_SUBDIR = "logos";

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

    @Override
    @Transactional
    public Tenant create(TenantRegistrationRequest request, MultipartFile logoFile) {
        // If a logo file was uploaded, store it and use its path as the logoUrl.
        if (logoFile != null && !logoFile.isEmpty()) {
            String logoPath = fileStorageService.storeImage(logoFile, LOGO_SUBDIR);
            request.setLogoUrl(logoPath);
        }
        return create(request);
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
    @Transactional(readOnly = true)
    public Page<Tenant> findAll(Pageable pageable) {
        return tenantRepository.findAll(pageable);
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

    @Override
    @Transactional
    public Tenant updateLogo(Long id, MultipartFile logoFile) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found with ID: " + id, String.valueOf(id)));

        String previousLogo = tenant.getLogoUrl();

        // Store the new logo and persist only the file path (never base64/bytes).
        String logoPath = fileStorageService.storeImage(logoFile, LOGO_SUBDIR);
        tenant.setLogoUrl(logoPath);
        Tenant saved = tenantRepository.save(tenant);

        // Best-effort cleanup of the old file once the new one is committed.
        if (StringUtils.hasText(previousLogo) && !previousLogo.equals(logoPath)) {
            fileStorageService.deleteByWebPath(previousLogo);
        }

        log.info("Tenant logo updated: identifier={}, logoUrl={}",
                saved.getTenantIdentifier(), saved.getLogoUrl());
        return saved;
    }
}
