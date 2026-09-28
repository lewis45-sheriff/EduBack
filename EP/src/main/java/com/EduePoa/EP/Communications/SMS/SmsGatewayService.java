package com.EduePoa.EP.Communications.SMS;

import java.util.List;

public interface SmsGatewayService {

    /** Send a single SMS to one recipient. */
    SmsDispatchResult sendSms(String phoneNumber, String message);

    /**
     * Send the same message to many recipients in a single Africa's Talking bulk request.
     * Numbers are normalised and de-duplicated before dispatch.
     */
    BulkSmsDispatchResult sendBulkSms(List<String> phoneNumbers, String message);
}
