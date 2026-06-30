package com.EduePoa.EP.Multitenancy.controller;

import com.EduePoa.EP.Multitenancy.dto.TenantLifecycleActionRequest;
import com.EduePoa.EP.Multitenancy.dto.TenantRegistrationRequest;
import com.EduePoa.EP.Multitenancy.dto.TenantResponse;
import com.EduePoa.EP.Multitenancy.entity.Tenant;
import com.EduePoa.EP.Multitenancy.service.TenantLifecycleService;
import com.EduePoa.EP.Multitenancy.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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


    @GetMapping
    public ResponseEntity<List<TenantResponse>> listAllTenants() {
        List<TenantResponse> tenants = tenantService.findAll().stream()
                .map(this::toResponse)
                .toList();
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
