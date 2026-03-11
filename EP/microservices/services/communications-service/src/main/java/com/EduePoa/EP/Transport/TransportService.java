package com.EduePoa.EP.Transport;

import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.TransportTransactions.Requests.TransportTransactionRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface TransportService {

    CustomResponse<?> create(TransportRequestDTO transportRequestDTO);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> getAll();

    CustomResponse<?> update(Long id, TransportRequestDTO transportRequestDTO);

    CustomResponse<?> delete(Long id);
    CustomResponse<?>assign(AssignTransportRequestDTO request);
    CustomResponse<?>assignments();
    CustomResponse<?>deleteAssignments(Long id);
    CustomResponse<?>studentTransport();
    CustomResponse<?>createTransportTransaction(Long id,TransportTransactionRequestDTO transportTransactionRequestDTO);
    CustomResponse<?>getAllTransportTransactions();
    CustomResponse<?>getUtilizationSummary();
}
