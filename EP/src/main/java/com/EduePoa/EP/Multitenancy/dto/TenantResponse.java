package com.EduePoa.EP.Multitenancy.dto;

import com.EduePoa.EP.Multitenancy.entity.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    private Long id;

    private String tenantIdentifier;

    private String schoolName;

    private String physicalAddress;

    private String emailDomain;

    private String phoneNumber;

    private String logoUrl;

    private TenantStatus status;

    private String subscriptionPlan;

    private Timestamp createdOn;

    private Timestamp updatedOn;
}
