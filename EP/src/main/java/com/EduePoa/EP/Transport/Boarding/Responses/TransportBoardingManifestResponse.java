package com.EduePoa.EP.Transport.Boarding.Responses;

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
 * Expected-vs-actual manifest for a vehicle/date/leg. {@code missing = expected - actual} keyed by
 * studentId, where "expected" is derived from {@code AssignTransport} filtered by the centralized
 * {@code eligibleLegs(...)} rule (the same rule the scan endpoint uses).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportBoardingManifestResponse {

    private Long vehicleId;
    private String vehicleNumber;
    private String route;
    private LocalDate date;
    private BoardingLeg leg;

    private int expectedCount;
    private int boardedCount;
    private int missingCount;

    private List<BoardedStudent> boarded;
    private List<MissingStudent> missing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardedStudent {
        private Long studentId;
        private String admissionNumber;
        private String fullName;
        private TransportType transportType;
        /**
         * Legs this student is expected on, derived from the same {@code eligibleLegs(...)} rule the
         * scan endpoint uses. Ordered MORNING_PICKUP, MORNING_DROPOFF, EVENING_PICKUP,
         * EVENING_DROPOFF. Never null for a valid assignment.
         */
        private List<BoardingLeg> applicableLegs;
        private CaptureMethod method;
        private LocalDateTime capturedAt;
        private BoardingStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingStudent {
        private Long studentId;
        private String admissionNumber;
        private String fullName;
        private TransportType transportType;
        /**
         * Legs this student is expected on, derived from the same {@code eligibleLegs(...)} rule the
         * scan endpoint uses. Ordered MORNING_PICKUP, MORNING_DROPOFF, EVENING_PICKUP,
         * EVENING_DROPOFF. Never null for a valid assignment.
         */
        private List<BoardingLeg> applicableLegs;
    }
}
