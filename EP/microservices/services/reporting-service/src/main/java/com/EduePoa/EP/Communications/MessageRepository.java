package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Communications.Enums.MessageStatus;
import com.EduePoa.EP.Communications.Enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByIdAndDeletedFlag(Long id, char deletedFlag);

    Page<Message> findByDeletedFlag(char deletedFlag, Pageable pageable);

    Page<Message> findByStatusAndDeletedFlag(MessageStatus status, char deletedFlag, Pageable pageable);

    Page<Message> findByMessageTypeAndDeletedFlag(MessageType messageType, char deletedFlag, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.deletedFlag = 'N' " +
            "AND (:status IS NULL OR m.status = :status) " +
            "AND (:messageType IS NULL OR m.messageType = :messageType) " +
            "AND (:startDate IS NULL OR m.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.createdAt <= :endDate)")
    Page<Message> findWithFilters(
            @Param("status") MessageStatus status,
            @Param("messageType") MessageType messageType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    List<Message> findByStatusAndDeletedFlag(MessageStatus status, char deletedFlag);

    @Query("SELECT m FROM Message m WHERE m.status = 'SCHEDULED' AND m.deletedFlag = 'N' ORDER BY m.scheduledAt ASC")
    List<Message> findScheduledMessages();
}
