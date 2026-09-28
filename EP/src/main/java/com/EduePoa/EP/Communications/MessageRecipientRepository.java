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

    /**
     * Look up a recipient by the provider (Africa's Talking) message id. Used by the delivery-report
     * callback, which arrives without tenant context — a native query bypasses the tenant Hibernate
     * filter so the row can be found regardless of the active tenant.
     */
    @Query(value = "SELECT * FROM message_recipients WHERE provider_message_id = :providerMessageId LIMIT 1", nativeQuery = true)
    java.util.Optional<MessageRecipient> findByProviderMessageIdNative(@Param("providerMessageId") String providerMessageId);
}
