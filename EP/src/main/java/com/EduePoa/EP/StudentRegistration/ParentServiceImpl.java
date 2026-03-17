package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Response.ParentResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentServiceImpl implements ParentService {

    private final ParentRepository parentRepository;

    @Override
    public CustomResponse<?> getAllParents() {
        CustomResponse<List<ParentResponseDTO>> response = new CustomResponse<>();
        try {
            List<ParentResponseDTO> parents = parentRepository.findAll().stream()
                    .filter(p -> p.getDeletedFlag() == 'N')
                    .map(p -> ParentResponseDTO.builder()
                            .id(p.getId())
                            .firstName(p.getFirstName())
                            .lastName(p.getLastName())
                            .otherNames(p.getOtherNames())
                            .fullName(buildFullName(p.getFirstName(), p.getOtherNames(), p.getLastName()))
                            .phoneNumber(p.getPhoneNumber())
                            .alternatePhoneNumber(p.getAlternatePhoneNumber())
                            .email(p.getEmail())
                            .nationalIdOrPassport(p.getNationalIdOrPassport())
                            .occupation(p.getOccupation())
                            .address(p.getAddress())
                            .portalAccessEnabled(p.isPortalAccessEnabled())
                            .build())
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parents fetched successfully");
            response.setEntity(parents);
            log.info("Fetched {} parents", parents.size());
        } catch (Exception e) {
            log.error("Error fetching parents: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching parents: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    private String buildFullName(String firstName, String otherNames, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null) sb.append(firstName.trim());
        if (otherNames != null && !otherNames.trim().isEmpty()) sb.append(" ").append(otherNames.trim());
        if (lastName != null) sb.append(" ").append(lastName.trim());
        return sb.toString().trim();
    }
}
