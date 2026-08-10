package com.EduePoa.EP.MPesa;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Filter;

import java.io.Serializable;
import java.sql.Timestamp;

@ToString
@Data
@EqualsAndHashCode(callSuper = false, of = {"id"})
@DynamicUpdate
@Entity
@Table(name = "mpesa_stk_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "mpesa_transaction_id", columnNames = {"id"}),
        @UniqueConstraint(name = "mpesa_transaction_merchant_request_id", columnNames = {"merchant_request_id"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class    MpesaSTKTransactions extends TenantScopedEntity implements Serializable {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "merchant_request_id")
    private String merchantRequestID;

    @Column(name = "checkout_request_id")
    private String checkoutRequestID;

    @Column(name = "customer_message")
    private String customerMessage;

    @Column(name = "response_description")
    private String responseDescription;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "amount")
    private Double amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studentId")
    private Student accountReference;

    @CreationTimestamp
    @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
    @Column(name = "create_date", nullable = false)
    private Timestamp createdDate;
}
