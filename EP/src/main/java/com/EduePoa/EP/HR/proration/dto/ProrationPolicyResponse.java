package com.EduePoa.EP.HR.proration.dto;

import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.enums.ProrationRounding;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ProrationPolicyResponse {
    private Long id;
    private Integer version;
    private LocalDate effectiveFrom;
    private String description;
    private boolean prorateOnJoin;
    private boolean prorateOnLeave;
    private boolean startDateInclusive;
    private boolean endDateInclusive;
    private DayCountMethod dayCountMethod;
    private String workingDaysMask;
    private boolean unpaidDaysSupported;
    private FixedAllowanceProrationRule fixedAllowanceRule;
    private ProrationRounding rounding;
    private int roundingScale;
    private LocalDateTime createdAt;
    private String createdBy;
}
