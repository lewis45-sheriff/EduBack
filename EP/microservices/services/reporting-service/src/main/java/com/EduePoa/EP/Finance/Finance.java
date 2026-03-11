package com.EduePoa.EP.Finance;

import com.EduePoa.EP.Authentication.Enum.Term;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "finance")
public class Finance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private BigDecimal totalFeeAmount;

    @Column(nullable = false)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private BigDecimal balance;

    private LocalDateTime lastUpdated;
    @Enumerated(EnumType.STRING)  // Add this annotation
    @Column(length = 20)
    private Term term;
    private Year year;
}
