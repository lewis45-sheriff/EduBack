package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeComponents.Requests.FeeComponentRequest;
import com.EduePoa.EP.FeeComponents.Responses.FeeComponentResponse;
import com.EduePoa.EP.FeeComponents.Responses.FeeStructureFormatedDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeeComponentsServiceImpl implements FeeComponentsService {
    private final FeeComponentsRepository feeComponentsRepository;
    private final AuditService auditService;

    @Override
    public CustomResponse<?> create(FeeComponentRequest feeComponentRequest) {
        CustomResponse<FeeComponentResponse> response = new CustomResponse<>();
        try {
            // Basic validation
            if (feeComponentRequest.getName() == null || feeComponentRequest.getName().trim().isEmpty()) {
                response.setEntity(null);
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Name is required");
                return response;
            }

            // Map request to entity
            FeeComponents feeComponents = FeeComponents.builder()
                    .name(feeComponentRequest.getName().trim())
                    .description(
                            feeComponentRequest.getDescription() != null ? feeComponentRequest.getDescription().trim()
                                    : null)
                    .type(feeComponentRequest.getType())
                    .category(feeComponentRequest.getCategory())
                    .status(Status.ACTIVE)
                    .build();

            // Save to DB
            FeeComponents savedComponent = feeComponentsRepository.save(feeComponents);

            // Map entity → DTO
            FeeComponentResponse dto = FeeComponentResponse.builder()
                    .id(savedComponent.getId())
                    .name(savedComponent.getName())
                    .description(savedComponent.getDescription())
                    .type(savedComponent.getType())
                    .category(savedComponent.getCategory())
                    .status(String.valueOf(savedComponent.getStatus()))
                    .build();

            // Build response
            response.setEntity(dto);
            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Fee component created successfully");
            auditService.log("FEE_COMPONENTS", "Created fee component:", savedComponent.getName(), "with ID:",
                    String.valueOf(savedComponent.getId()));

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAll() {
        CustomResponse<List<FeeComponentResponse>> response = new CustomResponse<>();
        try {
            // Fetch all from DB
            List<FeeComponents> feeComponentsList = feeComponentsRepository.findAll();

            // Map entities → response DTOs
            List<FeeComponentResponse> responseList = feeComponentsList.stream()
                    .map(fc -> FeeComponentResponse.builder()
                            .id(fc.getId())
                            .name(fc.getName())
                            .description(fc.getDescription())
                            .type(fc.getType())
                            .category(fc.getCategory())
                            .status(String.valueOf(fc.getStatus()))
                            .timeCreated(fc.getCreatedOn())
                            .build())
                    .toList();

            // Build response
            response.setEntity(responseList);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Fee components retrieved successfully");
            auditService.log("FEE_COMPONENTS", "Retrieved", String.valueOf(responseList.size()), "fee components");

        } catch (RuntimeException e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    public CustomResponse<?> delete(Long id) {
        CustomResponse<String> response = new CustomResponse<>();
        try {
            // Find by ID
            Optional<FeeComponents> optionalComponent = feeComponentsRepository.findById(id);

            if (optionalComponent.isEmpty()) {
                response.setEntity(null);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Fee component with ID " + id + " not found");
                return response;
            }

            // Set deleted flag = true
            FeeComponents feeComponent = optionalComponent.get();
            feeComponent.setDeleted(true);
            feeComponentsRepository.save(feeComponent);

            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Fee component soft deleted successfully");
            auditService.log("FEE_COMPONENTS", "Deleted fee component:", feeComponent.getName(), "with ID:",
                    String.valueOf(id));

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllForFeeStructure() {
        CustomResponse<List<FeeStructureFormatedDTO>> response = new CustomResponse<>();
        try {
            List<FeeComponents> feeComponents = feeComponentsRepository.findAll();

            // Map FeeComponents -> FeeStructureFormatedDTO
            List<FeeStructureFormatedDTO> formattedList = feeComponents.stream()
                    .map(fc -> FeeStructureFormatedDTO.builder()
                            .id(fc.getId())
                            .name(fc.getName())
                            .build())
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Fee components retrieved successfully");
            response.setEntity(formattedList);
            auditService.log("FEE_COMPONENTS", "Retrieved", String.valueOf(formattedList.size()),
                    "fee components for fee structure");

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error retrieving fee components: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

}
