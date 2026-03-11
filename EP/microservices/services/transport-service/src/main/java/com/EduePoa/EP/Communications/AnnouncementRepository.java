package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Communications.Enums.AnnouncementPriority;
import com.EduePoa.EP.Communications.Enums.AnnouncementStatus;
import com.EduePoa.EP.Communications.Enums.TargetAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByIdAndDeletedFlag(Long id, char deletedFlag);

    Page<Announcement> findByDeletedFlag(char deletedFlag, Pageable pageable);

    Page<Announcement> findByStatusAndDeletedFlag(AnnouncementStatus status, char deletedFlag, Pageable pageable);

    Page<Announcement> findByPriorityAndDeletedFlag(AnnouncementPriority priority, char deletedFlag, Pageable pageable);

    Page<Announcement> findByTargetAudienceAndDeletedFlag(TargetAudience targetAudience, char deletedFlag,
            Pageable pageable);

    @Query("SELECT a FROM Announcement a WHERE a.deletedFlag = 'N' " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:priority IS NULL OR a.priority = :priority) " +
            "AND (:targetAudience IS NULL OR a.targetAudience = :targetAudience)")
    Page<Announcement> findWithFilters(
            @Param("status") AnnouncementStatus status,
            @Param("priority") AnnouncementPriority priority,
            @Param("targetAudience") TargetAudience targetAudience,
            Pageable pageable);

    List<Announcement> findByStatusAndDeletedFlag(AnnouncementStatus status, char deletedFlag);
}
