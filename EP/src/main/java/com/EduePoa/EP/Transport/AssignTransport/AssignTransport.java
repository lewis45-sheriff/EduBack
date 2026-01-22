package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "student_transport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignTransport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    @JsonIgnore
    private Transport vehicle;

    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;

    @Column(name = "transport_type", nullable = false)
    private String transportType;
}
