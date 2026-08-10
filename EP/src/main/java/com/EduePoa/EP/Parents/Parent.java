package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Entity
@Table(name = "parent")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Parent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String otherNames;

    @Column(unique = true)
    private String phoneNumber;

    private String alternatePhoneNumber;

    @Column(unique = true)
    private String email;

    private String nationalIdOrPassport;
    private String occupation;
    private String address;

    private boolean portalAccessEnabled = false;
    private boolean receiveSms = true;
    private boolean receiveEmail = false;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @CreationTimestamp
    private Timestamp createdOn;

    private char deletedFlag = 'N';
}
