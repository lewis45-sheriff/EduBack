package com.EduePoa.EP.HR.compensation.service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.HR.compensation.dto.request.AllowanceRequest;
import com.EduePoa.EP.HR.compensation.dto.request.CompensationRequest;
import com.EduePoa.EP.HR.compensation.dto.request.RecurringDeductionRequest;
import com.EduePoa.EP.HR.compensation.dto.response.AllowanceResponse;
import com.EduePoa.EP.HR.compensation.dto.response.CompensationResponse;
import com.EduePoa.EP.HR.compensation.dto.response.RecurringDeductionResponse;
import com.EduePoa.EP.HR.compensation.entity.Allowance;
import com.EduePoa.EP.HR.compensation.entity.EmployeeCompensation;
import com.EduePoa.EP.HR.compensation.entity.RecurringDeduction;
import com.EduePoa.EP.HR.compensation.repository.AllowanceRepository;
import com.EduePoa.EP.HR.compensation.repository.EmployeeCompensationRepository;
import com.EduePoa.EP.HR.compensation.repository.RecurringDeductionRepository;
import com.EduePoa.EP.Staff.Staff;
import com.EduePoa.EP.Staff.StaffRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationServiceImpl implements CompensationService {

    private final EmployeeCompensationRepository compensationRepository;
    private final AllowanceRepository allowanceRepository;
    private final RecurringDeductionRepository recurringDeductionRepository;
    private final StaffRepository staffRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public CustomResponse<?> saveCompensation(CompensationRequest request) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            Staff staff = staffRepository.findById(request.getStaffId()).orElse(null);
            if (staff == null) {
                return notFound(response, "Staff not found with ID: " + request.getStaffId());
            }

            EmployeeCompensation comp = compensationRepository.findByStaffId(request.getStaffId())
                    .orElseGet(EmployeeCompensation::new);
            boolean isNew = comp.getId() == null;

            comp.setStaff(staff);
            comp.setBasicSalary(request.getBasicSalary());
            comp.setCurrency(request.getCurrency() != null ? request.getCurrency() : "KES");
            comp.setNssfPensionablePayOverride(request.getNssfPensionablePayOverride());
            comp.setInsurancePremiumMonthly(
                    request.getInsurancePremiumMonthly() != null
                            ? request.getInsurancePremiumMonthly() : java.math.BigDecimal.ZERO);
            comp.setActive(request.getActive() == null || request.getActive());
            comp.setEffectiveFrom(request.getEffectiveFrom() != null
                    ? request.getEffectiveFrom() : LocalDate.now());

            // Replace-semantic for lines: clear and rebuild from the request.
            comp.getAllowances().clear();
            comp.getRecurringDeductions().clear();

            EmployeeCompensation saved = compensationRepository.save(comp);

            if (request.getAllowances() != null) {
                for (AllowanceRequest a : request.getAllowances()) {
                    saved.getAllowances().add(buildAllowance(saved, a));
                }
            }
            if (request.getRecurringDeductions() != null) {
                for (RecurringDeductionRequest d : request.getRecurringDeductions()) {
                    saved.getRecurringDeductions().add(buildDeduction(saved, d));
                }
            }
            saved = compensationRepository.save(saved);

            auditService.log("PAYROLL_COMPENSATION",
                    isNew ? "Created" : "Updated", "compensation for staff:",
                    String.valueOf(staff.getId()), "basic:", String.valueOf(saved.getBasicSalary()));

            response.setStatusCode(isNew ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
            response.setMessage("Compensation saved successfully");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            log.error("Error saving compensation: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to save compensation: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getByStaffId(Long staffId) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            EmployeeCompensation comp = compensationRepository.findByStaffId(staffId).orElse(null);
            if (comp == null) {
                return notFound(response, "No compensation found for staff ID: " + staffId);
            }
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Compensation retrieved successfully");
            response.setEntity(toDto(comp));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve compensation: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> list(int page, int size) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();
        try {
            Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 10 : size);
            Page<EmployeeCompensation> p = compensationRepository.findAll(pageable);
            List<CompensationResponse> content = p.getContent().stream().map(this::toDto).toList();
            Map<String, Object> result = Map.of(
                    "content", content,
                    "currentPage", p.getNumber(),
                    "totalItems", p.getTotalElements(),
                    "totalPages", p.getTotalPages());
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(content.isEmpty() ? "No compensation records found"
                    : "Compensation records retrieved successfully");
            response.setEntity(result);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to list compensation: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> addAllowance(Long staffId, AllowanceRequest request) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            EmployeeCompensation comp = compensationRepository.findByStaffId(staffId).orElse(null);
            if (comp == null) {
                return notFound(response, "No compensation found for staff ID: " + staffId);
            }
            comp.getAllowances().add(buildAllowance(comp, request));
            EmployeeCompensation saved = compensationRepository.save(comp);
            auditService.log("PAYROLL_COMPENSATION", "Added allowance", request.getName(),
                    "to staff:", String.valueOf(staffId));
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Allowance added");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to add allowance: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> deleteAllowance(Long staffId, Long allowanceId) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            EmployeeCompensation comp = compensationRepository.findByStaffId(staffId).orElse(null);
            if (comp == null) {
                return notFound(response, "No compensation found for staff ID: " + staffId);
            }
            boolean removed = comp.getAllowances().removeIf(a -> a.getId().equals(allowanceId));
            if (!removed) {
                return notFound(response, "Allowance " + allowanceId + " not found on staff " + staffId);
            }
            EmployeeCompensation saved = compensationRepository.save(comp);
            auditService.log("PAYROLL_COMPENSATION", "Removed allowance", String.valueOf(allowanceId),
                    "from staff:", String.valueOf(staffId));
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Allowance removed");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to remove allowance: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> addDeduction(Long staffId, RecurringDeductionRequest request) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            EmployeeCompensation comp = compensationRepository.findByStaffId(staffId).orElse(null);
            if (comp == null) {
                return notFound(response, "No compensation found for staff ID: " + staffId);
            }
            comp.getRecurringDeductions().add(buildDeduction(comp, request));
            EmployeeCompensation saved = compensationRepository.save(comp);
            auditService.log("PAYROLL_COMPENSATION", "Added deduction", request.getName(),
                    "to staff:", String.valueOf(staffId));
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Deduction added");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to add deduction: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> deleteDeduction(Long staffId, Long deductionId) {
        CustomResponse<CompensationResponse> response = new CustomResponse<>();
        try {
            EmployeeCompensation comp = compensationRepository.findByStaffId(staffId).orElse(null);
            if (comp == null) {
                return notFound(response, "No compensation found for staff ID: " + staffId);
            }
            boolean removed = comp.getRecurringDeductions().removeIf(d -> d.getId().equals(deductionId));
            if (!removed) {
                return notFound(response, "Deduction " + deductionId + " not found on staff " + staffId);
            }
            EmployeeCompensation saved = compensationRepository.save(comp);
            auditService.log("PAYROLL_COMPENSATION", "Removed deduction", String.valueOf(deductionId),
                    "from staff:", String.valueOf(staffId));
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Deduction removed");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to remove deduction: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    // ---- mapping helpers ----

    private Allowance buildAllowance(EmployeeCompensation comp, AllowanceRequest a) {
        return Allowance.builder()
                .compensation(comp)
                .name(a.getName())
                .basis(a.getBasis())
                .amount(a.getAmount())
                .taxable(a.isTaxable())
                .pensionable(a.isPensionable())
                .fixedMonthly(a.isFixedMonthly())
                .build();
    }

    private RecurringDeduction buildDeduction(EmployeeCompensation comp, RecurringDeductionRequest d) {
        return RecurringDeduction.builder()
                .compensation(comp)
                .name(d.getName())
                .basis(d.getBasis())
                .amount(d.getAmount())
                .preTax(d.isPreTax())
                .startDate(d.getStartDate())
                .endDate(d.getEndDate())
                .build();
    }

    private CompensationResponse toDto(EmployeeCompensation c) {
        Staff s = c.getStaff();
        return CompensationResponse.builder()
                .id(c.getId())
                .staffId(s != null ? s.getId() : null)
                .staffName(s != null ? fullName(s) : null)
                .employeeNumber(s != null ? s.getEmployeeNumber() : null)
                .basicSalary(c.getBasicSalary())
                .currency(c.getCurrency())
                .nssfPensionablePayOverride(c.getNssfPensionablePayOverride())
                .insurancePremiumMonthly(c.getInsurancePremiumMonthly())
                .active(c.isActive())
                .effectiveFrom(c.getEffectiveFrom())
                .allowances(c.getAllowances().stream().map(this::toAllowanceDto).toList())
                .recurringDeductions(c.getRecurringDeductions().stream().map(this::toDeductionDto).toList())
                .build();
    }

    private AllowanceResponse toAllowanceDto(Allowance a) {
        return AllowanceResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .basis(a.getBasis())
                .amount(a.getAmount())
                .taxable(a.isTaxable())
                .pensionable(a.isPensionable())
                .fixedMonthly(a.isFixedMonthly())
                .build();
    }

    private RecurringDeductionResponse toDeductionDto(RecurringDeduction d) {
        return RecurringDeductionResponse.builder()
                .id(d.getId())
                .name(d.getName())
                .basis(d.getBasis())
                .amount(d.getAmount())
                .preTax(d.isPreTax())
                .startDate(d.getStartDate())
                .endDate(d.getEndDate())
                .build();
    }

    private static String fullName(Staff s) {
        StringBuilder sb = new StringBuilder();
        if (s.getFirstName() != null) sb.append(s.getFirstName().trim());
        if (s.getOtherNames() != null && !s.getOtherNames().isBlank()) sb.append(" ").append(s.getOtherNames().trim());
        if (s.getLastName() != null) sb.append(" ").append(s.getLastName().trim());
        return sb.toString().trim();
    }

    private CustomResponse<CompensationResponse> notFound(CustomResponse<CompensationResponse> r, String msg) {
        r.setStatusCode(HttpStatus.NOT_FOUND.value());
        r.setMessage(msg);
        r.setEntity(null);
        return r;
    }
}
