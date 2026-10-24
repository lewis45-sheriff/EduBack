package com.EduePoa.EP.HR.config.service;

import com.EduePoa.EP.HR.config.entity.*;
import com.EduePoa.EP.HR.config.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Resolves the effective statutory configuration for the current tenant as of a given date
 * into an immutable {@link StatutoryConfigSnapshot}. The snapshot is what a payroll run
 * freezes, so runs stay reproducible after rates change.
 * <p>
 * All reads run under the ambient tenant filter, so a tenant only ever resolves its own
 * overrides. National defaults are seeded per tenant (see {@code StatutoryConfigSeeder}),
 * so resolution is a single tenant-scoped lookup — a tenant with no override still finds
 * the seeded national default row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatutoryConfigService {

    private final PayeBandRepository payeBandRepository;
    private final ReliefConfigRepository reliefConfigRepository;
    private final NssfConfigRepository nssfConfigRepository;
    private final ShifConfigRepository shifConfigRepository;
    private final HousingLevyConfigRepository housingLevyConfigRepository;
    private final NitaConfigRepository nitaConfigRepository;

    /**
     * Resolve the complete statutory snapshot effective on {@code asOf} for the current tenant.
     *
     * @throws StatutoryConfigException if any required statutory component has no row effective
     *                                  on or before {@code asOf}. Callers surface this as a
     *                                  blocking validation error on the run.
     */
    public StatutoryConfigSnapshot resolveFor(LocalDate asOf) {
        if (asOf == null) {
            throw new StatutoryConfigException("A resolution date is required to resolve statutory config");
        }

        // PAYE bands: pin the latest effective set in force on/before asOf.
        LocalDate bandEffectiveFrom = payeBandRepository.findLatestEffectiveFromOnOrBefore(asOf);
        if (bandEffectiveFrom == null) {
            throw new StatutoryConfigException(
                    "No PAYE bands configured effective on or before " + asOf);
        }
        List<PayeBand> bands = payeBandRepository.findByEffectiveFromOrderBySequenceAsc(bandEffectiveFrom);
        if (bands.isEmpty()) {
            throw new StatutoryConfigException("No PAYE bands found for effective date " + bandEffectiveFrom);
        }

        ReliefConfig relief = reliefConfigRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(asOf, Limit.of(1))
                .orElseThrow(() -> new StatutoryConfigException(
                        "No PAYE relief configuration effective on or before " + asOf));

        NssfConfig nssf = nssfConfigRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(asOf, Limit.of(1))
                .orElseThrow(() -> new StatutoryConfigException(
                        "No NSSF configuration effective on or before " + asOf));

        ShifConfig shif = shifConfigRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(asOf, Limit.of(1))
                .orElseThrow(() -> new StatutoryConfigException(
                        "No SHIF configuration effective on or before " + asOf));

        HousingLevyConfig ahl = housingLevyConfigRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(asOf, Limit.of(1))
                .orElseThrow(() -> new StatutoryConfigException(
                        "No Housing Levy configuration effective on or before " + asOf));

        NitaConfig nita = nitaConfigRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(asOf, Limit.of(1))
                .orElseThrow(() -> new StatutoryConfigException(
                        "No NITA configuration effective on or before " + asOf));

        List<StatutoryConfigSnapshot.Band> snapshotBands = bands.stream()
                .map(b -> StatutoryConfigSnapshot.Band.builder()
                        .sequence(b.getSequence())
                        .lowerBound(b.getLowerBound())
                        .upperBound(b.getUpperBound())
                        .rate(b.getRate())
                        .build())
                .toList();

        return StatutoryConfigSnapshot.builder()
                .asOf(asOf)
                .payeBands(snapshotBands)
                .personalReliefMonthly(relief.getPersonalReliefMonthly())
                .insuranceReliefRate(relief.getInsuranceReliefRate())
                .insuranceReliefCap(relief.getInsuranceReliefCap())
                .nssfLowerEarningsLimit(nssf.getLowerEarningsLimit())
                .nssfUpperEarningsLimit(nssf.getUpperEarningsLimit())
                .nssfEmployeeRate(nssf.getEmployeeRate())
                .nssfEmployerRate(nssf.getEmployerRate())
                .shifRate(shif.getRate())
                .shifMinimumContribution(shif.getMinimumContribution())
                .ahlEmployeeRate(ahl.getEmployeeRate())
                .ahlEmployerRate(ahl.getEmployerRate())
                .ahlPreTaxDeductible(ahl.isPreTaxDeductible())
                .ahlReliefRate(ahl.getReliefRate() != null ? ahl.getReliefRate() : java.math.BigDecimal.ZERO)
                .nitaAmountPerEmployee(nita.getAmountPerEmployee())
                .build();
    }
}
