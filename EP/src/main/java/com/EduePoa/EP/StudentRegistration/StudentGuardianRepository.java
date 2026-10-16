package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGuardianRepository extends TenantAwareRepository<StudentGuardian, Long> {
    List<StudentGuardian> findByStudentId(Long studentId);

    /**
     * All guardian links for a given parent, i.e. the parent's children. The student graph is
     * eagerly fetched so the caller can build the response without an N+1 lookup.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"student"})
    List<StudentGuardian> findByParent_Id(Long parentId);

    /**
     * Resolve the guardian link between a specific parent and a specific student.
     * Used to verify that an authenticated parent is actually associated with the
     * target student before allowing parent-initiated financial actions.
     */
    java.util.Optional<StudentGuardian> findByStudent_IdAndParent_Id(Long studentId, Long parentId);
}
