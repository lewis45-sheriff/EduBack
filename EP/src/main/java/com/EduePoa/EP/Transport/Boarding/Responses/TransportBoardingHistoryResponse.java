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
 * A student's transport attendance history grouped by service date. Only actual recorded events are
 * returned — missing legs are never fabricated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportBoardingHistoryResponse {

    private Long studentId;
    private TransportType transportType;
    private LocalDate from;
    private LocalDate to;
    private List<Day> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate serviceDate;
        private List<Event> events;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private BoardingLeg leg;
        private LocalDateTime capturedAt;
        private CaptureMethod method;
        private BoardingStatus status;
        /** GPS latitude captured at boarding, if the device supplied one; otherwise null. */
        private Double latitude;
        /** GPS longitude captured at boarding, if the device supplied one; otherwise null. */
        private Double longitude;
        /** Username of the operator who recorded this event; null for system/legacy records. */
        private String recordedBy;
    }
}
