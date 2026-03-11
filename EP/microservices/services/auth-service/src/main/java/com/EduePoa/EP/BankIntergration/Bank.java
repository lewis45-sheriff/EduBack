package com.EduePoa.EP.BankIntergration;

import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "bank_transactions")
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Callback Type
    @Column(name = "callback_type")
    @JsonProperty("callbackType")
    private String callbackType; // IPN

    // Customer Information
    @Column(name = "customer_name")
    @JsonProperty("customer.name")
    private String customerName;

    @Column(name = "mobile_number")
    @JsonProperty("customer.mobileNumber")
    private String mobileNumber;

    @Column(name = "customer_reference")
    @JsonProperty("customer.reference")
    private String customerReference;

    // Transaction Information
    @Column(name = "transaction_date")
    @JsonProperty("transaction.date")
    private String transactionDate; // Or use LocalDateTime with custom deserializer

    @Column(name = "transaction_reference")
    @JsonProperty("transaction.reference")
    private String transactionReference;

    @Column(name = "payment_mode")
    @JsonProperty("transaction.paymentMode")
    private String paymentMode; // CARD, MPESA, PWE, EQUITEL, PAYPAL

    @Column(name = "amount", precision = 10, scale = 2)
    @JsonProperty("transaction.amount")
    private BigDecimal amount;

    @Column(name = "currency")
    @JsonProperty("transaction.currency")
    private String currency;

    @Column(name = "bill_number")
    @JsonProperty("transaction.billNumber")
    private String billNumber;

    @Column(name = "served_by")
    @JsonProperty("transaction.servedBy")
    private String servedBy;

    @Column(name = "additional_info")
    @JsonProperty("transaction.additionalInfo")
    private String additionalInfo;

    @Column(name = "order_amount", precision = 10, scale = 2)
    @JsonProperty("transaction.orderAmount")
    private BigDecimal orderAmount;

    @Column(name = "service_charge", precision = 10, scale = 2)
    @JsonProperty("transaction.serviceCharge")
    private BigDecimal serviceCharge;

    @Column(name = "order_currency")
    @JsonProperty("transaction.orderCurrency")
    private String orderCurrency;

    @Column(name = "status")
    @JsonProperty("transaction.status")
    private String status; // SUCCESS, FAILED

    @Column(name = "remarks")
    @JsonProperty("transaction.remarks")
    private String remarks;

    // Bank Information
    @Column(name = "bank_reference")
    @JsonProperty("bank.reference")
    private String bankReference;

    @Column(name = "transaction_type")
    @JsonProperty("bank.transactionType")
    private String transactionType; // C for Credit

    @Column(name = "account_number")
    @JsonProperty("bank.account")
    private String accountNumber;

    // Relationships
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = true)
    @JsonIgnore
    private Student student;
    private String narrative;

    // Audit fields
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JsonIgnore
//    @JoinColumn(name = "school_id", referencedColumnName = "id")
//    private School school;
}