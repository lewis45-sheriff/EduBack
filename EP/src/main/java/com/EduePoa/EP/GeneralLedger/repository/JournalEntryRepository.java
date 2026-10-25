package com.EduePoa.EP.GeneralLedger.repository;

import com.EduePoa.EP.GeneralLedger.entity.JournalEntry;
import com.EduePoa.EP.GeneralLedger.enums.GlSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /** Idempotency lookup for a non-reversal source event (tenant-scoped by filter). */
    Optional<JournalEntry> findBySourceTypeAndSourceId(GlSourceType sourceType, Long sourceId);

    boolean existsBySourceTypeAndSourceId(GlSourceType sourceType, Long sourceId);

    Optional<JournalEntry> findByReversalOfJournalId(Long reversalOfJournalId);

    boolean existsByReversalOfJournalId(Long reversalOfJournalId);

    /** Highest numeric suffix used for a tenant's entry numbers, for safe numbering. */
    @Query("select coalesce(max(j.id), 0) from JournalEntry j")
    long maxId();

    @Query("""
            select j from JournalEntry j
            where (:sourceType is null or j.sourceType = :sourceType)
              and (:from is null or j.accountingDate >= :from)
              and (:to is null or j.accountingDate <= :to)
            order by j.accountingDate desc, j.id desc
            """)
    Page<JournalEntry> search(@Param("sourceType") GlSourceType sourceType,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              Pageable pageable);

    /** Finds journals whose source references do not resolve (reconciliation). */
    List<JournalEntry> findBySourceType(GlSourceType sourceType);
}
