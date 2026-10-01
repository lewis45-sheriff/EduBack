package com.EduePoa.EP.Transport.Boarding.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.Boarding.BoardingLeg;
import com.EduePoa.EP.Transport.Boarding.BoardingStatus;
import com.EduePoa.EP.Transport.Boarding.CaptureMethod;
import com.EduePoa.EP.Transport.TransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One row of the transport student roster. Represents a student's transport assignment, optionally
 * enriched with that student's boarding events for a specific service date.
 *
 * <p>Produced by {@code GET /api/v1/transport/boarding/students}, where both {@code vehicleId} and
 * {@code date} are optional filters. The {@code events} list is only populated when {@code date} is
 * supplied; otherwise it is null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportStudentRosterResponse {

    private Long assignmentId;

    private Long studentId;
    private String admissionNumber;
    private String fullName;

    private Long vehicleId;
    private String vehicleNumber;
    private String route;

    private String pickupLocation;
    private TransportType transportType;

    /**
     * The legs this student can still be recorded on for the requested service date, derived from
     * the same expectation + one-trip-per-day rule the scan endpoint enforces. For a ONE_WAY
     * student this narrows to the claimed phase once they have boarded that day; without a date it
     * is all four legs. Ordered by the canonical sequence (MORNING_PICKUP, MORNING_DROPOFF,
     * EVENING_PICKUP, EVENING_DROPOFF). Never null; empty only if the student has no expected legs
     * (should not normally happen).
     */
    private List<BoardingLeg> applicableLegs;

    private Term term;
    private Integer year;
    private LocalDate assignmentDate;

    /**
     * Boarding events recorded for this student on the requested {@code date}. Null when no date
     * filter was supplied; empty when a date was supplied but nothing was recorded.
     */
    private List<RosterEvent> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RosterEvent {
        private Long eventId;
        private BoardingLeg leg;
        private LocalDateTime capturedAt;
        private CaptureMethod method;
        private BoardingStatus status;
    }
}
