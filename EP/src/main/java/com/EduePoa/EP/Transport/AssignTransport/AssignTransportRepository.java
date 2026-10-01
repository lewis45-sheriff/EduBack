package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignTransportRepository extends TenantAwareRepository<AssignTransport, Long> {

    List<AssignTransport> findByStudent(Student student);

    /**
     * Re-fetches an assignment with a {@code PESSIMISTIC_WRITE} row lock. Used by the scan pipeline
     * to serialize concurrent boarding events for the same ONE_WAY student on a given day, so the
     * "first phase claims the day" lockout is enforced atomically even across devices/offline sync.
     * The assignment row (one per student/term/year) always exists at this point, so it is a stable
     * lock target even for the day's very first event.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssignTransport a where a.id = :id")
    Optional<AssignTransport> findByIdForUpdate(@Param("id") Long id);

    Optional<AssignTransport> findByStudentAndTermAndYear(Student student, Term term, Integer year);

    boolean existsByStudentAndTermAndYear(Student student, Term term, Integer year);

    long countByVehicle(Transport vehicle);

    List<AssignTransport> findByVehicle(Transport vehicle);

    List<AssignTransport> findByVehicleAndTermAndYear(Transport vehicle, Term term, Integer year);

    /**
     * All transport assignments for a given term/year (across vehicles), used to derive the arrears
     * roster: every assigned student is expected to pay, whether or not they have any transaction.
     */
    List<AssignTransport> findByTermAndYear(Term term, Integer year);
}
