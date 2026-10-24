package com.EduePoa.EP.HR.proration.service;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.HR.proration.dto.ProrationPolicyRequest;
import com.EduePoa.EP.HR.proration.dto.ProrationPolicyResponse;
import com.EduePoa.EP.HR.proration.entity.ProrationPolicy;
import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.repository.ProrationPolicyRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin management of the versioned proration policy. Creating a policy always creates a NEW
 * immutable version (previous max + 1); it never edits an existing version. This guarantees
 * that a run which snapshotted version N stays reproducible even after version N+1 is created.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProrationPolicyService {

    private final ProrationPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CustomResponse<?> createVersion(ProrationPolicyRequest request) {
        CustomResponse<ProrationPolicyResponse> response = new CustomResponse<>();
        try {
            if (request.getDayCountMethod() == DayCountMethod.WORKING_DAYS) {
                String mask = request.getWorkingDaysMask();
                if (mask == null || mask.length() != 7 || !mask.matches("[YyNn]{7}")) {
                    response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    response.setMessage("workingDaysMask must be a 7-character Mon..Sun mask of Y/N "
                            + "when dayCountMethod is WORKING_DAYS");
                    return response;
                }
            }
            if (request.getRoundingScale() < 0 || request.getRoundingScale() > 6) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("roundingScale must be between 0 and 6");
                return response;
            }

            int nextVersion = policyRepository.findFirstByOrderByVersionDesc()
                    .map(p -> p.getVersion() + 1)
                    .orElse(1);

            ProrationPolicy policy = ProrationPolicy.builder()
                    .version(nextVersion)
                    .effectiveFrom(request.getEffectiveFrom())
                    .description(request.getDescription())
                    .prorateOnJoin(request.isProrateOnJoin())
                    .prorateOnLeave(request.isProrateOnLeave())
                    .startDateInclusive(request.isStartDateInclusive())
                    .endDateInclusive(request.isEndDateInclusive())
                    .dayCountMethod(request.getDayCountMethod())
                    .workingDaysMask(request.getWorkingDaysMask())
                    .unpaidDaysSupported(request.isUnpaidDaysSupported())
                    .fixedAllowanceRule(request.getFixedAllowanceRule())
                    .rounding(request.getRounding())
                    .roundingScale(request.getRoundingScale())
                    .createdBy(currentUsername())
                    .build();

            ProrationPolicy saved = policyRepository.save(policy);
            auditService.log("PAYROLL_PRORATION", "Created proration policy version",
                    String.valueOf(saved.getVersion()), "effectiveFrom:",
                    String.valueOf(saved.getEffectiveFrom()));

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Proration policy version " + nextVersion + " created");
            response.setEntity(toDto(saved));
        } catch (RuntimeException e) {
            log.error("Error creating proration policy: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to create proration policy: " + e.getMessage());
        }
        return response;
    }

    public CustomResponse<?> listVersions() {
        CustomResponse<List<ProrationPolicyResponse>> response = new CustomResponse<>();
        try {
            List<ProrationPolicyResponse> all = policyRepository
                    .findAll(Sort.by(Sort.Direction.DESC, "version"))
                    .stream().map(this::toDto).toList();
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(all.isEmpty() ? "No proration policies configured" : "Policies retrieved");
            response.setEntity(all);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to list proration policies: " + e.getMessage());
        }
        return response;
    }

    /** The policy effective on {@code asOf} (defaults to today if null). */
    public CustomResponse<?> getEffective(LocalDate asOf) {
        CustomResponse<ProrationPolicyResponse> response = new CustomResponse<>();
        try {
            LocalDate date = asOf != null ? asOf : LocalDate.now();
            ProrationPolicy policy = policyRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(date, Limit.of(1))
                    .orElse(null);
            response.setStatusCode(HttpStatus.OK.value());
            if (policy == null) {
                response.setMessage("No stored proration policy; the system default (calendar days, "
                        + "inclusive dates, fixed allowances prorated, HALF_UP) applies");
                response.setEntity(null);
            } else {
                response.setMessage("Effective proration policy retrieved");
                response.setEntity(toDto(policy));
            }
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to resolve effective proration policy: " + e.getMessage());
        }
        return response;
    }

    private ProrationPolicyResponse toDto(ProrationPolicy p) {
        return ProrationPolicyResponse.builder()
                .id(p.getId())
                .version(p.getVersion())
                .effectiveFrom(p.getEffectiveFrom())
                .description(p.getDescription())
                .prorateOnJoin(p.isProrateOnJoin())
                .prorateOnLeave(p.isProrateOnLeave())
                .startDateInclusive(p.isStartDateInclusive())
                .endDateInclusive(p.isEndDateInclusive())
                .dayCountMethod(p.getDayCountMethod())
                .workingDaysMask(p.getWorkingDaysMask())
                .unpaidDaysSupported(p.isUnpaidDaysSupported())
                .fixedAllowanceRule(p.getFixedAllowanceRule())
                .rounding(p.getRounding())
                .roundingScale(p.getRoundingScale())
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .build();
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return "SYSTEM";
            }
            return userRepository.findByEmail(auth.getName())
                    .map(User::getUsername)
                    .orElse(auth.getName());
        } catch (RuntimeException e) {
            return "SYSTEM";
        }
    }
}
