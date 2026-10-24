package com.EduePoa.EP.HR.proration.dto;

import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.enums.ProrationRounding;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Create a new version of the proration policy. The version number is assigned by the server
 * (previous max + 1); policies are immutable once created, so this always creates a new row.
 */
@Data
public class ProrationPolicyRequest {

    @NotNull(message = "effectiveFrom is required")
    private LocalDate effectiveFrom;

    private String description;

    private boolean prorateOnJoin = true;
    private boolean prorateOnLeave = true;
    private boolean startDateInclusive = true;
    private boolean endDateInclusive = true;

    @NotNull(message = "dayCountMethod is required")
    private DayCountMethod dayCountMethod = DayCountMethod.CALENDAR_DAYS;

    /** 7-char Mon..Sun mask, e.g. "YYYYYNN". Required when dayCountMethod = WORKING_DAYS. */
    private String workingDaysMask = "YYYYYNN";

    private boolean unpaidDaysSupported = true;

    @NotNull(message = "fixedAllowanceRule is required")
    private FixedAllowanceProrationRule fixedAllowanceRule = FixedAllowanceProrationRule.PRORATE;

    @NotNull(message = "rounding is required")
    private ProrationRounding rounding = ProrationRounding.HALF_UP;

    private int roundingScale = 2;
}
