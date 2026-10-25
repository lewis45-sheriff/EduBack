package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRepository extends TenantAwareRepository<Staff, Long> {
    Optional<Staff> findByEmail(String email);
    Optional<Staff> findByPhoneNumber(String phoneNumber);
    Optional<Staff> findByEmployeeNumber(String employeeNumber);
    Optional<Staff> findByUserId(Long userId);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmployeeNumber(String employeeNumber);

    /**
     * All non-deleted staff whose linked system user holds the given role name (e.g. ROLE_TEACHER).
     * Resolves "teachers" from the actual login role in the database rather than inferring from
     * {@link com.EduePoa.EP.Staff.Enum.StaffType}. Tenant-scoped via the Hibernate filter.
     */
    @org.springframework.data.jpa.repository.Query("""
            select s from Staff s
            join s.user u
            join u.role r
            where r.name = :roleName
              and s.deletedFlag = 'N'
            """)
    java.util.List<Staff> findAllByUserRoleName(@org.springframework.data.repository.query.Param("roleName") String roleName);
}
