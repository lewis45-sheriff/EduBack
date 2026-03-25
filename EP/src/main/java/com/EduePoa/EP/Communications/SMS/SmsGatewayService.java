package com.EduePoa.EP.Communications.SMS;

public interface SmsGatewayService {
    SmsDispatchResult sendSms(String phoneNumber, String message);
}
