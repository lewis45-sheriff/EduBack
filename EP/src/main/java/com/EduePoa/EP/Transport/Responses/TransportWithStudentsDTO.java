package com.EduePoa.EP.Transport.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A single vehicle together with the students currently assigned to it. Produced by
 * {@code GET /api/v1/transport/vehicle/{id}/students}. Additive to {@link TransportResponseDTO}:
 * carries the same vehicle fields plus an {@code assignedStudents} roster and a lightweight
 * capacity summary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportWithStudentsDTO {

    private Long id;
    private String vehicleNumber;
    private String vehicleType;
    private Integer capacity;
    private String driverName;
    private String driverContact;
    private String route;
    private String status;

    /** Number of students currently assigned to this vehicle. */
    private int assignedCount;

    /** Assigned students. Empty (never null) when the vehicle has no assignments. */
    @Builder.Default
    private List<AssignedStudent> assignedStudents = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignedStudent {
        private Long assignmentId;
        private Long studentId;
        private String admissionNumber;
        private String fullName;
        private String pickupLocation;
        private TransportType transportType;
        private Term term;
        private Integer year;
        private LocalDate assignmentDate;
    }
}
