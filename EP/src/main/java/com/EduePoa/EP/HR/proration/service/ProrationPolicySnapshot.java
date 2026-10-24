package com.EduePoa.EP.HR.proration.service;

import com.EduePoa.EP.HR.proration.enums.DayCountMethod;
import com.EduePoa.EP.HR.proration.enums.FixedAllowanceProrationRule;
import com.EduePoa.EP.HR.proration.enums.ProrationRounding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Immutable snapshot of the {@link com.EduePoa.EP.HR.proration.entity.ProrationPolicy} in force
 * for a payroll run. Frozen onto the run (serialized to JSON) so the proration behaviour is
 * exactly reproducible even if the tenant later changes its policy. The {@link #policyVersion}
 * records which versioned policy produced the run.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProrationPolicySnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer policyVersion;
    private LocalDate effectiveFrom;

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
}
