package com.EduePoa.EP.Multitenancy.controller;

import com.EduePoa.EP.Multitenancy.dto.TenantLifecycleActionRequest;
import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.dto.TenantResponse;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.service.TenantLifecycleService;
import com.EduePoa.EP.Multitenancy.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("api/v1/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Platform_Admin')")
public class TenantController {

    private final TenantService tenantService;
    private final TenantLifecycleService tenantLifecycleService;


    @PostMapping
    public ResponseEntity<TenantResponse> registerTenant(
            @Valid @RequestBody TenantRegistrationRequest request) {
        Tenant tenant = tenantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tenant));
    }

    /**
     * Register a new school/tenant with an optional logo file in one request.
     * <p>
     * Send as {@code multipart/form-data}: a {@code request} part containing the
     * JSON tenant details and an optional {@code logo} file part. The logo is
     * stored on disk and only its file path is persisted on the tenant.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenantResponse> registerTenantWithLogo(
            @Valid @RequestPart("request") TenantRegistrationRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        Tenant tenant = tenantService.create(request, logo);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tenant));
    }

    /**
     * Upload or replace the logo for an existing tenant. The image is stored on
     * disk and the tenant's {@code logoUrl} is set to the served file path.
     */
    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenantResponse> uploadLogo(
            @PathVariable Long id,
            @RequestPart("logo") MultipartFile logo) {
        Tenant tenant = tenantService.updateLogo(id, logo);
        return ResponseEntity.ok(toResponse(tenant));
    }


    /**
     * List all tenants across the platform, paginated. This is a platform-admin
     * endpoint and is intentionally NOT scoped to a single tenant: it returns
     * tenants from every tenant in the system.
     *
     * @param page 1-based page number (defaults to 1)
     * @param size page size (defaults to 20)
     */
    @GetMapping
    public ResponseEntity<Page<TenantResponse>> listAllTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = size < 1 ? 20 : size;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());
        Page<TenantResponse> tenants = tenantService.findAll(pageable).map(this::toResponse);
        return ResponseEntity.ok(tenants);
    }

    /**
     * Check if a tenant identifier is available for registration.
     */
    @GetMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@RequestParam String identifier) {
        boolean available = !tenantService.existsByIdentifier(identifier);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("identifier", identifier);
        body.put("available", available);
        if (!available) {
            body.put("suggestion", identifier + "-2");
        }
        return ResponseEntity.ok(body);
    }


    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable Long id) {
        Tenant tenant = tenantService.findById(id);
        return ResponseEntity.ok(toResponse(tenant));
    }


    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantRegistrationRequest request) {
        Tenant tenant = tenantService.update(id, request);
        return ResponseEntity.ok(toResponse(tenant));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<TenantResponse> suspendTenant(@PathVariable Long id, @Valid @RequestBody TenantLifecycleActionRequest request, Authentication authentication) {
        Tenant tenant = tenantService.findById(id);
        Tenant suspended = tenantLifecycleService.suspend(
                tenant.getTenantIdentifier(),
                request.getReason(),
                authentication.getName());
        return ResponseEntity.ok(toResponse(suspended));
    }

    /**
     * Reactivate a suspended tenant, restoring normal access for all its users.
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<TenantResponse> reactivateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantLifecycleActionRequest request,
            Authentication authentication) {
        Tenant tenant = tenantService.findById(id);
        Tenant reactivated = tenantLifecycleService.reactivate(
                tenant.getTenantIdentifier(),
                request.getReason(),
                authentication.getName());
        return ResponseEntity.ok(toResponse(reactivated));
    }


    @PostMapping("/{id}/decommission")
    public ResponseEntity<TenantResponse> decommissionTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantLifecycleActionRequest request,
            Authentication authentication) {
        Tenant tenant = tenantService.findById(id);
        Tenant decommissioned = tenantLifecycleService.decommission(
                tenant.getTenantIdentifier(),
                request.getReason(),
                authentication.getName());
        return ResponseEntity.ok(toResponse(decommissioned));
    }


    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantIdentifier(tenant.getTenantIdentifier())
                .schoolName(tenant.getSchoolName())
                .physicalAddress(tenant.getPhysicalAddress())
                .emailDomain(tenant.getEmailDomain())
                .phoneNumber(tenant.getPhoneNumber())
                .logoUrl(tenant.getLogoUrl())
                .status(tenant.getStatus())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .createdOn(tenant.getCreatedOn())
                .updatedOn(tenant.getUpdatedOn())
                .build();
    }
}
