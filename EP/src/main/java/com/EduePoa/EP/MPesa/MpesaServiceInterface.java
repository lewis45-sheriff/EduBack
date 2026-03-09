package com.EduePoa.EP.MPesa;

import com.EduePoa.EP.Utils.CustomResponse;
import io.micrometer.common.lang.NonNull;

public interface MpesaServiceInterface {
    String generateToken( );
    MpesaPaymentResponseDTO initiateSTKPush(@NonNull String phoneNumber, @NonNull Double amount, Long accountReference );

    CustomResponse<?> processCallback(Object object);
//    CustomResponse<?> queryTransactionStatus(String transactionId);
}

// Service Implementation and other classes remain unchanged
