package com.EduePoa.EP.MPesa;

import io.micrometer.common.lang.NonNull;

public interface MpesaServiceInterface {
    String generateToken( );
    MpesaPaymentResponseDTO initiateSTKPush(@NonNull String phoneNumber, @NonNull Double amount, Long accountReference );

//    Object processCallback(Object object);
//    CustomResponse<?> queryTransactionStatus(String transactionId);
}

// Service Implementation and other classes remain unchanged
