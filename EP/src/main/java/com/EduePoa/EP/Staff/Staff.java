package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Staff.Enum.StaffType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Entity
@Table(name = "staff")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Staff extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String otherNames;

    @Column(unique = true)
    private String employeeNumber;

    @Column(unique = true)
    private String phoneNumber;

    private String alternatePhoneNumber;

    @Column(unique = true)
    private String email;

    private String nationalIdOrPassport;
    private String gender;
    private String address;

    @Enumerated(EnumType.STRING)
    private StaffType staffType;

    /** Free-text department / subject area, e.g. "Mathematics", "Finance". */
    private String department;

    /** Job title, e.g. "Senior Teacher", "Bursar". */
    private String designation;

    private LocalDate dateOfEmployment;

    /**
     * Whether this staff member has a login. Teachers are always granted portal
     * access; other staff types only when this flag is explicitly set.
     */
    private boolean portalAccessEnabled = false;

    private boolean receiveSms = true;
    private boolean receiveEmail = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @CreationTimestamp
    private Timestamp createdOn;

    @UpdateTimestamp
    private Timestamp updatedOn;

    private char deletedFlag = 'N';
}
