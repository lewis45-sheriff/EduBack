package com.EduePoa.EP.FeeStructure.FeeComponentConfig;

import com.EduePoa.EP.FeeStructure.FeeStructure;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
public class FeeComponentConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Integer id;

    @Column(unique = true)
    private String name;

    private String feeStatus;

    @CreatedDate
    @JsonIgnore
    private LocalDateTime createdAt;

    @LastModifiedDate
    @JsonIgnore
    private LocalDateTime updatedAt;
    private BigDecimal amount;
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdOn;
    @ManyToOne
    @JsonBackReference
    @JsonIgnore
    @JoinColumn(name = "fee_structure_id")
    private FeeStructure feeStructure;

    private String term;


}
