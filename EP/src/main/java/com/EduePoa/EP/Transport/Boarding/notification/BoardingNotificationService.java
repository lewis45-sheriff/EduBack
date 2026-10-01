package com.EduePoa.EP.Transport.Boarding.notification;


public interface BoardingNotificationService {

    void notifyForEvent(Long boardingEventId, String tenantId);
}
