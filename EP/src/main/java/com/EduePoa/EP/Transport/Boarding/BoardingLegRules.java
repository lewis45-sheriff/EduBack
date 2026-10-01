package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.Boarding.BoardingLeg.Phase;
import com.EduePoa.EP.Transport.TransportType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for which transport legs a student may board on, given their assignment
 * and what they have already boarded that day. Both the scan accept/reject decision and the
 * roster/manifest {@code applicableLegs} field derive from here, so the two can never diverge.
 *
 * <h2>The ONE_WAY "first phase claims the day" rule</h2>
 * A {@code ONE_WAY} student rides one trip per day — either the morning phase (MORNING_PICKUP +
 * MORNING_DROPOFF) or the evening phase (EVENING_PICKUP + EVENING_DROPOFF), never both. The first
 * phase in which a boarding is recorded on a given service date claims the day; the other phase is
 * then locked out. Until a phase is claimed, both are open. Within the claimed phase both legs
 * (pickup then dropoff) remain available.
 *
 * <p>{@code TWO_WAY} students are unaffected: both phases are always allowed.
 */
public final class BoardingLegRules {

    /**
     * Canonical leg order clients can rely on. {@link #applicableLegs} always returns a subset
     * preserving this ordering.
     */
    private static final List<BoardingLeg> CANONICAL_ORDER = List.of(
            BoardingLeg.MORNING_PICKUP,
            BoardingLeg.MORNING_DROPOFF,
            BoardingLeg.EVENING_PICKUP,
            BoardingLeg.EVENING_DROPOFF);

    private static final Set<BoardingLeg> ALL_FOUR = EnumSet.of(
            BoardingLeg.MORNING_PICKUP,
            BoardingLeg.MORNING_DROPOFF,
            BoardingLeg.EVENING_PICKUP,
            BoardingLeg.EVENING_DROPOFF);

    private BoardingLegRules() {
    }

    private static final Set<BoardingLeg> MORNING_PAIR =
            EnumSet.of(BoardingLeg.MORNING_PICKUP, BoardingLeg.MORNING_DROPOFF);

    private static final Set<BoardingLeg> EVENING_PAIR =
            EnumSet.of(BoardingLeg.EVENING_PICKUP, BoardingLeg.EVENING_DROPOFF);

    /**
     * The legs a leg's phase covers.
     */
    private static Set<BoardingLeg> legsOfPhase(Phase phase) {
        return phase == Phase.MORNING ? EnumSet.copyOf(MORNING_PAIR) : EnumSet.copyOf(EVENING_PAIR);
    }

    /**
     * Type-only eligibility, ignoring any day claim. {@code TWO_WAY} and {@code ONE_WAY} both
     * resolve to all four legs here, because a {@code ONE_WAY} student has not yet committed to a
     * phase until the day's first boarding. Day-specific narrowing is applied by
     * {@link #eligibleLegs(AssignTransport, Set)} and {@link #applicableLegs(AssignTransport, Set)}.
     *
     * @return all four legs for any valid assignment; empty only when the assignment/type is null
     */
    public static Set<BoardingLeg> eligibleLegs(AssignTransport assignment) {
        if (assignment == null || assignment.getTransportType() == null) {
            return EnumSet.noneOf(BoardingLeg.class);
        }
        return EnumSet.copyOf(ALL_FOUR);
    }

    /**
     * Day-aware eligibility: which legs may still be boarded given the phases already claimed that
     * service date. This is the authoritative rule the scan endpoint enforces.
     *
     * <ul>
     *   <li>{@code TWO_WAY} &rarr; all four, regardless of prior boardings.</li>
     *   <li>{@code ONE_WAY}, no phase claimed &rarr; all four (either phase still open).</li>
     *   <li>{@code ONE_WAY}, a phase claimed &rarr; only that claimed phase's two legs.</li>
     * </ul>
     *
     * @param assignment    the student's assignment (may be null)
     * @param boardedPhases phases the student already has an event in on the relevant service date
     *                      (may be null/empty when nothing boarded yet)
     */
    public static Set<BoardingLeg> eligibleLegs(AssignTransport assignment, Set<Phase> boardedPhases) {
        if (assignment == null || assignment.getTransportType() == null) {
            return EnumSet.noneOf(BoardingLeg.class);
        }
        if (assignment.getTransportType() == TransportType.TWO_WAY) {
            return EnumSet.copyOf(ALL_FOUR);
        }
        // ONE_WAY: the first claimed phase locks the day to that phase.
        Phase claimed = claimedPhase(boardedPhases);
        if (claimed == null) {
            return EnumSet.copyOf(ALL_FOUR);
        }
        return legsOfPhase(claimed);
    }

    /**
     * Day-aware applicable legs, ordered by the canonical sequence (MORNING_PICKUP, MORNING_DROPOFF,
     * EVENING_PICKUP, EVENING_DROPOFF). This is exactly {@link #eligibleLegs(AssignTransport, Set)}
     * projected onto that fixed order, so the array a client sees always matches the legs the scan
     * endpoint will accept for the student on that date.
     *
     * @return an ordered list; empty only when the assignment has no expected legs (should not
     *         normally happen for a valid assignment)
     */
    public static List<BoardingLeg> applicableLegs(AssignTransport assignment, Set<Phase> boardedPhases) {
        Set<BoardingLeg> eligible = eligibleLegs(assignment, boardedPhases);
        List<BoardingLeg> ordered = new ArrayList<>(eligible.size());
        for (BoardingLeg leg : CANONICAL_ORDER) {
            if (eligible.contains(leg)) {
                ordered.add(leg);
            }
        }
        return ordered;
    }

    /**
     * Applicable legs with no day context (e.g. roster/manifest requested without a date): a
     * {@code ONE_WAY} student has claimed nothing, so all four legs are applicable.
     */
    public static List<BoardingLeg> applicableLegs(AssignTransport assignment) {
        return applicableLegs(assignment, null);
    }

    /**
     * The phase a {@code ONE_WAY} student has committed the day to, or {@code null} if none is
     * claimed yet. If (through legacy/edge data) both phases somehow have events, MORNING is
     * treated as the claim so the result is deterministic.
     */
    public static Phase claimedPhase(Set<Phase> boardedPhases) {
        if (boardedPhases == null || boardedPhases.isEmpty()) {
            return null;
        }
        if (boardedPhases.contains(Phase.MORNING)) {
            return Phase.MORNING;
        }
        return Phase.EVENING;
    }
}
