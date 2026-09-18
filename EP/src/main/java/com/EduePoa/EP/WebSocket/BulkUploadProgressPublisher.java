package com.EduePoa.EP.WebSocket;

import com.EduePoa.EP.StudentRegistration.Request.BulkUploadProgressDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link BulkUploadProgressDTO} events to the per-job STOMP topic
 * {@code /topic/bulk-upload/{jobId}} that the frontend subscribes to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkUploadProgressPublisher {

    private static final String TOPIC_PREFIX = "/topic/bulk-upload/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(BulkUploadProgressDTO progress) {
        if (progress == null || progress.getJobId() == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + progress.getJobId(), progress);
        } catch (Exception e) {
            // Never let a messaging failure abort the upload itself.
            log.warn("Failed to publish bulk-upload progress for job {}: {}",
                    progress.getJobId(), e.getMessage());
        }
    }
}
