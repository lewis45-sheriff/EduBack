package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Communications.Enums.DeliveryStatus;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRecipientRepository extends TenantAwareRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageId(Long messageId);

    List<MessageRecipient> findByMessageIdAndDeliveryStatus(Long messageId, DeliveryStatus status);

    @Query("SELECT COUNT(mr) FROM MessageRecipient mr WHERE mr.message.id = :messageId")
    Long countByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(mr) FROM MessageRecipient mr WHERE mr.message.id = :messageId AND mr.deliveryStatus = :status")
    Long countByMessageIdAndStatus(@Param("messageId") Long messageId, @Param("status") DeliveryStatus status);
}
