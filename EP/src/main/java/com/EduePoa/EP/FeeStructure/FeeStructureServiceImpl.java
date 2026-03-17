package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.FeeComponents.FeeComponents;
import com.EduePoa.EP.FeeComponents.FeeComponentsRepository;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfigRepository;
import com.EduePoa.EP.FeeStructure.Requests.FeeStructureRequestDTO;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureGroupedResponseDTO;
import com.EduePoa.EP.FeeStructure.Responses.FeeStructureResponseDTO;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j

public class FeeStructureServiceImpl implements FeeStructureService {
    private final FeeStructureRepository feeStructureRepository;
    private final GradeRepository gradeRepository;
    private final FeeComponentsRepository feeComponentsRepository;
    private final FeeComponentConfigRepository feeComponentConfigRepository;
    private final AuditService auditService;

    private record FeeStructureComputation(List<FeeComponentConfig> components, double totalFeeAmount) {
    }

    @Override
    @Audit(module = "FEE STRUCTURE", action = "CREATE")
    public CustomResponse<?> create(FeeStructureRequestDTO request) {
        log.info("Creating fee structure: {}", request);
        CustomResponse<FeeStructure> response = new CustomResponse<>();

        try {
            // Validate Grade
            Optional<Grade> grade = gradeRepository.findByName(request.getGrade());
            if (grade.isEmpty()) {
                response.setMessage("Grade not found: " + request.getGrade());
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setEntity(null);
                return response;
            }

            // Create FeeStructure entity
            int currentYear = LocalDate.now().getYear();

            // After validating grade, check for existing fee structure
            Optional<FeeStructure> existingFeeStructure = feeStructureRepository
                    .findByGradeAndYear(grade.get(), currentYear);

            FeeStructure feeStructure;
            if (existingFeeStructure.isPresent()) {
                feeStructure = existingFeeStructure.get();
                feeStructure.setName("Fee Structure for " + request.getGrade());
                feeStructure.setGrade(grade.get());
                feeStructure.setDatePosted(LocalDateTime.now());
                feeStructure.setIsDeleted('N');
                feeStructure.setDeleted('N');
                feeStructure.getTermComponents().clear();
            } else {
                // Create new fee structure
                feeStructure = new FeeStructure();
                feeStructure.setYear(currentYear);
                feeStructure.setName("Fee Structure for " + request.getGrade());
                feeStructure.setGrade(grade.get());
                feeStructure.setDatePosted(LocalDateTime.now());
                feeStructure.setIsApproved('N');
                feeStructure.setIsDeleted('N');
                feeStructure.setDeleted('N');
                feeStructure.setIsRejected('N');
            }

            // Process terms and fee items
            FeeStructureComputation computation = buildFeeStructureComponents(request, feeStructure);

            // 4. Save FeeStructure
            feeStructure.setTotalAmount(computation.totalFeeAmount());
            feeStructure.setTermComponents(computation.components());
            feeStructureRepository.save(feeStructure);

            // 5. Prepare response
            response.setMessage("Fee Structure created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());
            auditService.log("FEE_STRUCTURE", "Created fee structure for grade:", request.getGrade(),
                    "with total amount:", String.valueOf(computation.totalFeeAmount()));
            response.setEntity(feeStructure);

        } catch (Exception e) {
            log.error("Error creating fee structure", e);
            response.setMessage("An unexpected error occurred while creating the fee structure: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    @Audit(module = "FEE STRUCTURE", action = "GET_ALL")
    public CustomResponse<?> getAllFeeStructures() {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            // Fetch all non-deleted fee structures with their components
            List<FeeStructure> feeStructures = feeStructureRepository
                    .findByIsDeletedAndDeletedOrderByDatePostedDesc('N', 'N');

            if (feeStructures.isEmpty()) {
                response.setEntity(new ArrayList<>());
                response.setMessage("No fee structures found");
                response.setStatusCode(HttpStatus.OK.value());
                return response;
            }

            // Transform to DTO format expected by frontend
            List<FeeStructureResponseDTO> responseList = feeStructures.stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());

            response.setEntity(responseList);
            response.setMessage("Fee structures retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FEE_STRUCTURE", "Retrieved", String.valueOf(responseList.size()), "fee structures");

        } catch (Exception e) {
            response.setEntity(null);
            response.setMessage("Error retrieving fee structures: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    @Audit(module = "FEE STRUCTURE", action = "GET_BY_ID")
    public CustomResponse<?> getFeeStructureById(Long id) {
        CustomResponse<FeeStructureGroupedResponseDTO> response = new CustomResponse<>();
        try {
            Optional<FeeStructure> optionalFeeStructure = feeStructureRepository.findById(id);
            if (optionalFeeStructure.isEmpty()) {
                response.setMessage("Fee structure Not Found");
                response.setEntity(null);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            FeeStructure feeStructure = optionalFeeStructure.get();

            FeeStructureGroupedResponseDTO dto = convertToGroupedResponseDTO(feeStructure);

            response.setMessage("Fee structure retrieved successfully");
            response.setEntity(dto);
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FEE_STRUCTURE", "Retrieved fee structure with ID:", String.valueOf(id), "for grade:",
                    dto.getGrade());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
            response.setMessage(e.getMessage());
        }

        return response;
    }

    @Override
    @Audit(module = "FEE STRUCTURE", action = "UPDATE")
    public CustomResponse<?> update(Long id, FeeStructureRequestDTO request) {
        CustomResponse<FeeStructureResponseDTO> response = new CustomResponse<>();
        try {
            Optional<FeeStructure> optionalFeeStructure = feeStructureRepository.findById(id);
            if (optionalFeeStructure.isEmpty()) {
                response.setMessage("Fee structure not found");
                response.setEntity(null);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            FeeStructure feeStructure = optionalFeeStructure.get();
            if (isDeleted(feeStructure)) {
                response.setMessage("Fee structure has been deleted");
                response.setEntity(null);
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            Optional<Grade> grade = gradeRepository.findByName(request.getGrade());
            if (grade.isEmpty()) {
                response.setMessage("Grade not found: " + request.getGrade());
                response.setEntity(null);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            Optional<FeeStructure> existingForGrade = feeStructureRepository.findByGrade(grade.get());
            if (existingForGrade.isPresent()
                    && !existingForGrade.get().getId().equals(id)
                    && !isDeleted(existingForGrade.get())) {
                response.setMessage("Another fee structure already exists for grade: " + request.getGrade());
                response.setEntity(null);
                response.setStatusCode(HttpStatus.CONFLICT.value());
                return response;
            }

            feeStructure.setName("Fee Structure for " + request.getGrade());
            feeStructure.setGrade(grade.get());
            feeStructure.setDatePosted(LocalDateTime.now());
            feeStructure.getTermComponents().clear();

            FeeStructureComputation computation = buildFeeStructureComponents(request, feeStructure);
            feeStructure.setTotalAmount(computation.totalFeeAmount());
            feeStructure.setTermComponents(computation.components());

            FeeStructure updatedFeeStructure = feeStructureRepository.save(feeStructure);

            response.setMessage("Fee structure updated successfully");
            response.setEntity(convertToResponseDTO(updatedFeeStructure));
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FEE_STRUCTURE", "Updated fee structure with ID:", String.valueOf(id),
                    "for grade:", request.getGrade());

        } catch (Exception e) {
            log.error("Error updating fee structure with ID {}", id, e);
            response.setMessage("An unexpected error occurred while updating the fee structure: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    @Audit(module = "FEE STRUCTURE", action = "DELETE")
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {
            Optional<FeeStructure> optionalFeeStructure = feeStructureRepository.findById(id);
            if (optionalFeeStructure.isEmpty()) {
                response.setMessage("Fee structure not found");
                response.setEntity(null);
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                return response;
            }

            FeeStructure feeStructure = optionalFeeStructure.get();
            if (isDeleted(feeStructure)) {
                response.setMessage("Fee structure already deleted");
                response.setEntity(null);
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            feeStructure.setIsDeleted('Y');
            feeStructure.setDeleted('Y');
            feeStructureRepository.save(feeStructure);

            response.setMessage("Fee structure deleted successfully");
            response.setEntity(null);
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("FEE_STRUCTURE", "Deleted fee structure with ID:", String.valueOf(id));

        } catch (Exception e) {
            log.error("Error deleting fee structure with ID {}", id, e);
            response.setMessage("An unexpected error occurred while deleting the fee structure: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }

        return response;
    }

    // Helper method to convert entity to DTO
    private FeeStructureResponseDTO convertToResponseDTO(FeeStructure feeStructure) {
        FeeStructureResponseDTO dto = new FeeStructureResponseDTO();
        dto.setId(feeStructure.getId());
        dto.setGrade(feeStructure.getGrade() != null ? feeStructure.getGrade().getName() : "Unknown");
        dto.setCreatedOn(feeStructure.getDatePosted());
        dto.setUpdatedOn(feeStructure.getDatePosted());

        // Convert fee components
        List<FeeStructureResponseDTO.FeeItemDTO> feeItems = getFeeItemDTOS(feeStructure);
        dto.setFeeItems(feeItems);

        return dto;
    }

    private static List<FeeStructureResponseDTO.FeeItemDTO> getFeeItemDTOS(FeeStructure feeStructure) {
        List<FeeStructureResponseDTO.FeeItemDTO> feeItems = new ArrayList<>();
        if (feeStructure.getTermComponents() != null) {
            for (FeeComponentConfig config : feeStructure.getTermComponents()) {
                FeeStructureResponseDTO.FeeItemDTO item = new FeeStructureResponseDTO.FeeItemDTO();
                item.setTerm(config.getTerm());
                item.setId(Long.valueOf(config.getId()));
                item.setName(config.getName() != null ? config.getName() : "Unknown");
                item.setAmount(config.getAmount() != null ? config.getAmount().doubleValue() : 0.0);
                feeItems.add(item);
            }
        }
        return feeItems;
    }

    private FeeStructureGroupedResponseDTO convertToGroupedResponseDTO(FeeStructure feeStructure) {
        FeeStructureGroupedResponseDTO dto = new FeeStructureGroupedResponseDTO();
        dto.setId(feeStructure.getId());
        dto.setGrade(feeStructure.getGrade() != null ? feeStructure.getGrade().getName() : "Unknown");
        dto.setCreatedOn(feeStructure.getDatePosted());
        dto.setUpdatedOn(feeStructure.getDatePosted());
        dto.setTerms(getGroupedTerms(feeStructure));
        return dto;
    }

    private List<FeeStructureGroupedResponseDTO.TermDTO> getGroupedTerms(FeeStructure feeStructure) {
        Map<String, List<FeeStructureGroupedResponseDTO.FeeItemDTO>> groupedByTerm = new LinkedHashMap<>();

        if (feeStructure.getTermComponents() != null) {
            for (FeeComponentConfig config : feeStructure.getTermComponents()) {
                String term = config.getTerm() != null ? config.getTerm() : "Unknown";
                groupedByTerm.computeIfAbsent(term, ignored -> new ArrayList<>())
                        .add(new FeeStructureGroupedResponseDTO.FeeItemDTO(
                                Long.valueOf(config.getId()),
                                config.getName() != null ? config.getName() : "Unknown",
                                config.getAmount() != null ? config.getAmount().doubleValue() : 0.0));
            }
        }

        return groupedByTerm.entrySet().stream()
                .map(entry -> new FeeStructureGroupedResponseDTO.TermDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private FeeStructureComputation buildFeeStructureComponents(FeeStructureRequestDTO request, FeeStructure feeStructure) {
        List<FeeComponentConfig> allComponents = new ArrayList<>();
        double totalFeeAmount = 0.0;

        for (FeeStructureRequestDTO.TermDTO termDTO : request.getTerms()) {
            for (FeeStructureRequestDTO.FeeItemDTO itemDTO : termDTO.getFeeItems()) {
                FeeComponents config = feeComponentsRepository.findByName(itemDTO.getName())
                        .orElseThrow(() -> new RuntimeException(
                                "Fee configuration not found: " + itemDTO.getName()));

                FeeComponentConfig component = new FeeComponentConfig();

                if ("Transport11".equalsIgnoreCase(config.getName())) {
                    component.setAmount(BigDecimal.ZERO);
                } else {
                    component.setAmount(BigDecimal.valueOf(itemDTO.getAmount()));
                }

                component.setName(config.getName());
                component.setFeeStatus(String.valueOf(config.getStatus()));
                component.setTerm(termDTO.getTerm());
                component.setFeeStructure(feeStructure);

                allComponents.add(component);
                totalFeeAmount += itemDTO.getAmount();
            }
        }

        return new FeeStructureComputation(allComponents, totalFeeAmount);
    }

    private boolean isDeleted(FeeStructure feeStructure) {
        return feeStructure.getIsDeleted() == 'Y' || feeStructure.getDeleted() == 'Y';
    }

}
