package com.EduePoa.EP.Procurement.DeliveryNote;


import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteApprovalRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Requests.DeliveryNoteRequestDTO;
import com.EduePoa.EP.Procurement.DeliveryNote.Responses.DeliveryNoteResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DeliveryNoteService {
    CustomResponse<?> create(DeliveryNoteRequestDTO deliveryNoteRequestDTO);

    CustomResponse<?> getById(Long id);

    CustomResponse<?> getAll();

    Page<DeliveryNoteResponseDTO> getAllPaged(Pageable pageable);

    CustomResponse<?> update(Long id, DeliveryNoteRequestDTO deliveryNoteRequestDTO);

    CustomResponse<?> delete(Long id);

    CustomResponse<?> approveDeliveryNote(DeliveryNoteApprovalRequestDTO request);

    CustomResponse<?> rejectDeliveryNote(DeliveryNoteApprovalRequestDTO request);
    CustomResponse<?>deliveryNotePerSupplierId(Long id);
}
