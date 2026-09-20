package com.EduePoa.EP.Multitenancy.service;

import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TenantService {

    Tenant create(TenantRegistrationRequest request);

    /**
     * Creates a tenant and, if a logo file is supplied, stores it on disk and
     * persists the resulting file path on the new tenant.
     *
     * @param request  the tenant registration details
     * @param logoFile optional logo image (may be {@code null} or empty)
     * @return the created tenant
     */
    Tenant create(TenantRegistrationRequest request, MultipartFile logoFile);

    Tenant findById(Long id);
    Tenant findByIdentifier(String identifier);
    boolean existsByIdentifier(String identifier);
    List<Tenant> findAll();
    Page<Tenant> findAll(Pageable pageable);
    Tenant update(Long id, TenantRegistrationRequest request);

    /**
     * Uploads (or replaces) a tenant's logo. The image is stored on disk and
     * only the resulting file path is persisted in {@code Tenant.logoUrl}.
     *
     * @param id       the tenant id
     * @param logoFile the uploaded logo image
     * @return the updated tenant
     */
    Tenant updateLogo(Long id, MultipartFile logoFile);
}
