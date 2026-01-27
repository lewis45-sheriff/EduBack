package com.EduePoa.EP.Transport.TransportTransactions;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.Transport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransportTransactionsRepository extends JpaRepository<TransportTransactions,Long> {
    List<TransportTransactions> findByTransport(Transport transport);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM TransportTransactions t " +
            "WHERE t.student.id = :studentId " +
            "AND t.transport.id = :transportId " +
            "AND t.term = :term " +
            "AND t.year = :year " +
            "AND t.transportType = :transportType")
    Double sumPaidAmountByStudentAndTransportAndTermAndYear(
            @Param("studentId") Long studentId,
            @Param("transportId") Long transportId,
            @Param("term") Term term,
            @Param("year") Integer year,
            @Param("transportType") String transportType
    );
    // In TransportTransactionsRepository

    @Query("SELECT t.arrearsAfterThis FROM TransportTransactions t " +
            "WHERE t.student.id = :studentId " +
            "AND t.transport.id = :transportId " +
            "AND t.term = :term " +
            "AND t.year = :year " +
            "AND t.transportType = :transportType " +
            "ORDER BY t.transactionTime DESC, t.id DESC")
    Double getLatestArrears(
            @Param("studentId") Long studentId,
            @Param("transportId") Long transportId,
            @Param("term") Term term,
            @Param("year") Integer year,
            @Param("transportType") String transportType
    );

    @Query("SELECT t FROM TransportTransactions t " +
            "WHERE t.student.id = :studentId " +
            "AND t.transport.id = :transportId " +
            "AND t.term = :term " +
            "AND t.year = :year " +
            "AND t.transportType = :transportType " +
            "ORDER BY t.transactionTime DESC, t.id DESC")
    List<TransportTransactions> findLatestByStudentAndTransportAndTermAndYear(
            @Param("studentId") Long studentId,
            @Param("transportId") Long transportId,
            @Param("term") Term term,
            @Param("year") Integer year,
            @Param("transportType") String transportType
    );

}
