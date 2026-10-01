package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransportBoardingEventRepository extends TenantAwareRepository<TransportBoardingEvent, Long> {

    Optional<TransportBoardingEvent> findByStudentAndServiceDateAndLeg(
            Student student, LocalDate serviceDate, BoardingLeg leg);

    boolean existsByStudentAndServiceDateAndLeg(
            Student student, LocalDate serviceDate, BoardingLeg leg);

    Optional<TransportBoardingEvent> findByClientEventId(String clientEventId);

    boolean existsByClientEventId(String clientEventId);

    /**
     * Actual roster for a vehicle/date/leg. The student graph is eagerly fetched to avoid N+1
     * lookups when building the manifest response.
     */
    @EntityGraph(attributePaths = {"student"})
    List<TransportBoardingEvent> findByVehicleAndServiceDateAndLeg(
            Transport vehicle, LocalDate serviceDate, BoardingLeg leg);

    /**
     * Student history within an inclusive date range, ordered for grouping by day then leg.
     * Queried by studentId to avoid loading the Student entity just to filter.
     */
    List<TransportBoardingEvent>
    findByStudentIdAndServiceDateBetweenOrderByServiceDateAscLegAsc(
            Long studentId, LocalDate from, LocalDate to);

    /**
     * Distinct student ids that already have an event for the given vehicle/date/leg — a light
     * projection for manifest "boarded vs missing" reconciliation.
     */
    @Query("select distinct e.student.id from TransportBoardingEvent e "
            + "where e.vehicle = :vehicle and e.serviceDate = :serviceDate and e.leg = :leg")
    List<Long> findBoardedStudentIds(
            @Param("vehicle") Transport vehicle,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("leg") BoardingLeg leg);

    /**
     * All events for a set of students on a single service date. Used to attach each student's
     * recorded legs to a roster row without an N+1 lookup. Returns empty when the id set is empty.
     */
    @EntityGraph(attributePaths = {"student"})
    List<TransportBoardingEvent> findByStudentIdInAndServiceDate(
            List<Long> studentIds, LocalDate serviceDate);
}
