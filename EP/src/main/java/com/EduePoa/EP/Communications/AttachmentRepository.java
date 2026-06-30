package com.EduePoa.EP.Communications;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.util.List;

public interface AttachmentRepository extends TenantAwareRepository<Attachment, Long> {

    List<Attachment> findByAnnouncementId(Long announcementId);
}
