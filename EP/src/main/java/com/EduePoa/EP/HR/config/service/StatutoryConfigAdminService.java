package com.EduePoa.EP.HR.config.service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.HR.config.dto.*;
import com.EduePoa.EP.HR.config.entity.*;
import com.EduePoa.EP.HR.config.repository.*;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Admin read/write of the effective-dated statutory configuration. Writes require
 * {@code payroll:config}; reads require {@code payroll:read}. Every write creates a NEW
 * effective-dated row (never mutating an existing one in place beyond closing it), so a run that
 * froze the config effective on its period stays reproducible. Processed runs read their stored
 * snapshot, so a later rate change here cannot alter an approved/paid run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatutoryConfigAdminService {

    private final PayeBandRepository payeBandRepository;
    private final ReliefConfigRepository reliefConfigRepository;
    private final NssfConfigRepository nssfConfigRepository;
    private final ShifConfigRepository shifConfigRepository;
    private final HousingLevyConfigRepository housingLevyConfigRepository;
    private final NitaConfigRepository nitaConfigRepository;
    private final StatutoryConfigService statutoryConfigService;
    private final AuditService auditService;

    public CustomResponse<?> getResolved(LocalDate asOf) {
        CustomResponse<StatutoryConfigSnapshot> response = new CustomResponse<>();
        try {
            LocalDate date = asOf != null ? asOf : LocalDate.now().withDayOfMonth(1);
            StatutoryConfigSnapshot snapshot = statutoryConfigService.resolveFor(date);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Resolved statutory configuration for " + date);
            response.setEntity(snapshot);
        } catch (StatutoryConfigException e) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to resolve statutory config: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public CustomResponse<?> updatePayeBands(PayeBandsUpdateRequest request) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (payeBandRepository.existsByEffectiveFrom(request.getEffectiveFrom())) {
                return conflict(response, "PAYE bands already exist effective " + request.getEffectiveFrom()
                        + "; choose a different effective date to supersede them");
            }
            for (PayeBandRequest b : request.getBands()) {
                payeBandRepository.save(PayeBand.builder()
                        .sequence(b.getSequence())
                        .lowerBound(b.getLowerBound())
                        .upperBound(b.getUpperBound())
                        .rate(b.getRate())
                        .effectiveFrom(request.getEffectiveFrom())
                        .build());
            }
            auditService.log("PAYROLL_CONFIG", "Updated PAYE bands effective", String.valueOf(request.getEffectiveFrom()),
                    "count:", String.valueOf(request.getBands().size()));
            return ok(response, "PAYE bands saved effective " + request.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save PAYE bands: " + e.getMessage());
        }
    }

    @Transactional
    public CustomResponse<?> updateNssf(NssfConfigRequest r) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (nssfConfigRepository.existsByEffectiveFrom(r.getEffectiveFrom())) {
                return conflict(response, "NSSF config already exists effective " + r.getEffectiveFrom());
            }
            nssfConfigRepository.save(NssfConfig.builder()
                    .lowerEarningsLimit(r.getLowerEarningsLimit())
                    .upperEarningsLimit(r.getUpperEarningsLimit())
                    .employeeRate(r.getEmployeeRate())
                    .employerRate(r.getEmployerRate())
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            auditService.log("PAYROLL_CONFIG", "Updated NSSF config effective", String.valueOf(r.getEffectiveFrom()));
            return ok(response, "NSSF config saved effective " + r.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save NSSF config: " + e.getMessage());
        }
    }

    @Transactional
    public CustomResponse<?> updateShif(ShifConfigRequest r) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (shifConfigRepository.existsByEffectiveFrom(r.getEffectiveFrom())) {
                return conflict(response, "SHIF config already exists effective " + r.getEffectiveFrom());
            }
            shifConfigRepository.save(ShifConfig.builder()
                    .rate(r.getRate())
                    .minimumContribution(r.getMinimumContribution())
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            auditService.log("PAYROLL_CONFIG", "Updated SHIF config effective", String.valueOf(r.getEffectiveFrom()));
            return ok(response, "SHIF config saved effective " + r.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save SHIF config: " + e.getMessage());
        }
    }

    @Transactional
    public CustomResponse<?> updateHousingLevy(HousingLevyConfigRequest r) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (housingLevyConfigRepository.existsByEffectiveFrom(r.getEffectiveFrom())) {
                return conflict(response, "Housing Levy config already exists effective " + r.getEffectiveFrom());
            }
            housingLevyConfigRepository.save(HousingLevyConfig.builder()
                    .employeeRate(r.getEmployeeRate())
                    .employerRate(r.getEmployerRate())
                    .preTaxDeductible(r.isPreTaxDeductible())
                    .reliefRate(r.getReliefRate() != null ? r.getReliefRate() : java.math.BigDecimal.ZERO)
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            auditService.log("PAYROLL_CONFIG", "Updated Housing Levy config effective",
                    String.valueOf(r.getEffectiveFrom()));
            return ok(response, "Housing Levy config saved effective " + r.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save Housing Levy config: " + e.getMessage());
        }
    }

    @Transactional
    public CustomResponse<?> updateNita(NitaConfigRequest r) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (nitaConfigRepository.existsByEffectiveFrom(r.getEffectiveFrom())) {
                return conflict(response, "NITA config already exists effective " + r.getEffectiveFrom());
            }
            nitaConfigRepository.save(NitaConfig.builder()
                    .amountPerEmployee(r.getAmountPerEmployee())
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            auditService.log("PAYROLL_CONFIG", "Updated NITA config effective", String.valueOf(r.getEffectiveFrom()));
            return ok(response, "NITA config saved effective " + r.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save NITA config: " + e.getMessage());
        }
    }

    @Transactional
    public CustomResponse<?> updateRelief(ReliefConfigRequest r) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            if (reliefConfigRepository.existsByEffectiveFrom(r.getEffectiveFrom())) {
                return conflict(response, "Relief config already exists effective " + r.getEffectiveFrom());
            }
            reliefConfigRepository.save(ReliefConfig.builder()
                    .personalReliefMonthly(r.getPersonalReliefMonthly())
                    .insuranceReliefRate(r.getInsuranceReliefRate())
                    .insuranceReliefCap(r.getInsuranceReliefCap())
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            auditService.log("PAYROLL_CONFIG", "Updated relief config effective", String.valueOf(r.getEffectiveFrom()));
            return ok(response, "Relief config saved effective " + r.getEffectiveFrom());
        } catch (RuntimeException e) {
            return error(response, "Failed to save relief config: " + e.getMessage());
        }
    }

    private CustomResponse<Object> ok(CustomResponse<Object> r, String msg) {
        r.setStatusCode(HttpStatus.CREATED.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<Object> conflict(CustomResponse<Object> r, String msg) {
        r.setStatusCode(HttpStatus.CONFLICT.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }

    private CustomResponse<Object> error(CustomResponse<Object> r, String msg) {
        r.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
