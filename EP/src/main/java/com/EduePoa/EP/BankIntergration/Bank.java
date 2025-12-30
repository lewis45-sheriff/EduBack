package com.EduePoa.EP.BankIntergration;


import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transType;
    private String transId;
    private String ftRef;
    private String transTime;
    private String transAmount;
    private String businessShortCode;
    private String billRefNumber;
    private String narrative;
    private String mobile;
    private String customerName;
    private String tranParticulars;
    private String accountNumber;
    private String statusCode;
    private String statusDescription;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = true)
    private Student student;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JsonIgnore
//    @JoinColumn(name = "school_id", referencedColumnName = "id")
//    private School school;
}