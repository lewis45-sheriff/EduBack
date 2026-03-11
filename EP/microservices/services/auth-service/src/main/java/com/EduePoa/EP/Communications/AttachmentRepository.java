package com.EduePoa.EP.Communications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByAnnouncementId(Long announcementId);
}
