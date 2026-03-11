package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_components")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")

public class FeeComponents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String type;
    private String category;
    private Status status ;
    @Column()
    private boolean deleted = false;
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdOn;
    @ManyToOne
    @JsonBackReference
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id")
    private FeeStructure feeStructure;

    private String term;
    private BigDecimal amount;

}
