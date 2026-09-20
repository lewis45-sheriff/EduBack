package com.EduePoa.EP.Communications.WhatsApp;

import com.EduePoa.EP.Communications.SMS.SmsDispatchResult;

/**
 * Gateway for sending WhatsApp messages. Reuses {@link SmsDispatchResult} as a
 * generic dispatch result (success flag, provider message id, error message) so
 * the Communications module can treat SMS, Email, and WhatsApp uniformly.
 */
public interface WhatsAppGatewayService {
    SmsDispatchResult sendWhatsApp(String phoneNumber, String message);
}
