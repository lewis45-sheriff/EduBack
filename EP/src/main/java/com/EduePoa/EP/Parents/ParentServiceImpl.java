package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Authentication.Email.EmailService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Parents.Request.CreateParentRequestDTO;
import com.EduePoa.EP.Parents.Request.ParentInfoDTO;
import com.EduePoa.EP.Parents.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Parents.Request.UpdateParentRequestDTO;
import com.EduePoa.EP.Parents.Response.ParentResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentServiceImpl implements ParentService {

    private static final String PARENT_ROLE_NAME = "ROLE_PARENT";
    private static final String TEMP_PASSWORD     = "Parent@1234";

    private final ParentRepository parentRepository;
    private final UserRepository   userRepository;
    private final RoleRepository   roleRepository;
    private final PasswordEncoder  passwordEncoder;
    private final EmailService     emailService;

    @Override
    public CustomResponse<?> createParent(CreateParentRequestDTO request) {
        CustomResponse<ParentResponseDTO> response = new CustomResponse<>();
        try {
            ParentInfoDTO dto = request.resolve();

            if (dto.getFirstName() == null || dto.getFirstName().isBlank()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("First name is required");
                return response;
            }
            if (dto.getLastName() == null || dto.getLastName().isBlank()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Last name is required");
                return response;
            }
            if (dto.getPhoneNumber() == null || dto.getPhoneNumber().isBlank()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Phone number is required");
                return response;
            }

            if (parentRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A parent with that phone number already exists");
                return response;
            }

            if (dto.getEmail() != null && !dto.getEmail().isBlank()
                    && parentRepository.existsByEmail(dto.getEmail())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A parent with that email already exists");
                return response;
            }

            Parent parent = getParent(dto);
            Parent saved  = parentRepository.save(parent);

            Long userId = null;
            if (saved.isPortalAccessEnabled()) {
                userId = provisionPortalUser(saved);
            }

            ParentResponseDTO responseDTO = ParentResponseDTO.builder()
                    .id(saved.getId())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .otherNames(saved.getOtherNames())
                    .fullName(buildFullName(saved.getFirstName(), saved.getOtherNames(), saved.getLastName()))
                    .phoneNumber(saved.getPhoneNumber())
                    .alternatePhoneNumber(saved.getAlternatePhoneNumber())
                    .email(saved.getEmail())
                    .nationalIdOrPassport(saved.getNationalIdOrPassport())
                    .occupation(saved.getOccupation())
                    .address(saved.getAddress())
                    .portalAccessEnabled(saved.isPortalAccessEnabled())
                    .userId(userId)
                    .build();

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Parent created successfully");
            response.setEntity(responseDTO);
            log.info("Created parent id={} name={} userId={}", saved.getId(), responseDTO.getFullName(), userId);

        } catch (Exception e) {
            log.error("Error creating parent: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error creating parent: " + e.getMessage());
        }
        return response;
    }

    @NotNull
    private static Parent getParent(ParentInfoDTO dto) {
        Parent parent = new Parent();
        parent.setFirstName(dto.getFirstName().trim());
        parent.setLastName(dto.getLastName().trim());
        parent.setOtherNames(dto.getOtherNames());
        parent.setPhoneNumber(dto.getPhoneNumber().trim());
        parent.setAlternatePhoneNumber(dto.getAlternatePhoneNumber());
        parent.setEmail(dto.getEmail());
        parent.setNationalIdOrPassport(dto.getNationalIdOrPassport());
        parent.setOccupation(dto.getOccupation());
        parent.setAddress(dto.getAddress());
        parent.setPortalAccessEnabled(dto.isPortalAccessEnabled());
        parent.setReceiveSms(dto.isReceiveSms());
        parent.setReceiveEmail(dto.isReceiveEmail());
        parent.setDeletedFlag('N');
        return parent;
    }

    private Long provisionPortalUser(Parent parent) {
        String email = parent.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }

        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).map(User::getId).orElse(null);
        }

        Role parentRole = roleRepository.findByName(PARENT_ROLE_NAME)
                .orElseThrow(() -> new RuntimeException(
                        "Role '" + PARENT_ROLE_NAME + "' not found. Please create it in the database first."));

        User user = User.builder()
                .firstName(parent.getFirstName())
                .lastName(parent.getLastName())
                .email(email)
                .username(email)
                .phoneNumber(parent.getPhoneNumber())
                .password(passwordEncoder.encode(TEMP_PASSWORD))
                .role(parentRole)
                .status(Status.ACTIVE)
                .enabledFlag('Y')
                .deletedFlag('N')
                .is_lockedFlag('N')
                .forcePasswordReset(true)
                .passwordReset(true)
                .build();

        User savedUser = userRepository.save(user);

        parent.setUser(savedUser);
        parentRepository.save(parent);
        sendWelcomeEmail(parent, email);

        return savedUser.getId();
    }

    private void sendWelcomeEmail(Parent parent, String email) {
        try {
            String fullName = buildFullName(parent.getFirstName(), parent.getOtherNames(), parent.getLastName());
            String subject  = "Welcome to the Parent Portal";
            String body     = "<p>Dear " + fullName + ",</p>"
                    + "<p>Your parent portal account has been created. Use the credentials below to log in:</p>"
                    + "<ul>"
                    + "<li><strong>Email / Username:</strong> " + email + "</li>"
                    + "<li><strong>Temporary Password:</strong> " + TEMP_PASSWORD + "</li>"
                    + "</ul>"
                    + "<p>You will be required to change your password on first login.</p>"
                    + "<p>Thank you.</p>";
            emailService.sendEmail(email, subject, body);
        } catch (Exception e) {
            log.warn("Welcome email could not be sent to {}: {}", email, e.getMessage());
        }
    }

    @Override
    public CustomResponse<?> getAllParents() {
        CustomResponse<List<ParentResponseDTO>> response = new CustomResponse<>();
        try {
            List<ParentResponseDTO> parents = parentRepository.findAll()
                    .stream()
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

    @Override
    public CustomResponse<?> getParentById(Long id) {
        CustomResponse<ParentResponseDTO> response = new CustomResponse<>();
        try {
            Parent parent = parentRepository.findById(id)
                    .filter(p -> p.getDeletedFlag() == 'N')
                    .orElse(null);

            if (parent == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Parent not found");
                return response;
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parent fetched successfully");
            response.setEntity(toResponseDTO(parent));
        } catch (Exception e) {
            log.error("Error fetching parent id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching parent: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> updateParent(Long id, UpdateParentRequestDTO request) {
        CustomResponse<ParentResponseDTO> response = new CustomResponse<>();
        try {
            Parent parent = parentRepository.findById(id)
                    .filter(p -> p.getDeletedFlag() == 'N')
                    .orElse(null);

            if (parent == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Parent not found");
                return response;
            }

            if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                    && !request.getPhoneNumber().equals(parent.getPhoneNumber())
                    && parentRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A parent with that phone number already exists");
                return response;
            }

            if (request.getEmail() != null && !request.getEmail().isBlank()
                    && !request.getEmail().equals(parent.getEmail())
                    && parentRepository.existsByEmail(request.getEmail())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A parent with that email already exists");
                return response;
            }

            if (request.getFirstName()            != null) parent.setFirstName(request.getFirstName().trim());
            if (request.getLastName()             != null) parent.setLastName(request.getLastName().trim());
            if (request.getOtherNames()           != null) parent.setOtherNames(request.getOtherNames());
            if (request.getPhoneNumber()          != null) parent.setPhoneNumber(request.getPhoneNumber().trim());
            if (request.getAlternatePhoneNumber() != null) parent.setAlternatePhoneNumber(request.getAlternatePhoneNumber());
            if (request.getEmail()                != null) parent.setEmail(request.getEmail());
            if (request.getNationalIdOrPassport() != null) parent.setNationalIdOrPassport(request.getNationalIdOrPassport());
            if (request.getOccupation()           != null) parent.setOccupation(request.getOccupation());
            if (request.getAddress()              != null) parent.setAddress(request.getAddress());
            if (request.getReceiveSms()           != null) parent.setReceiveSms(request.getReceiveSms());
            if (request.getReceiveEmail()         != null) parent.setReceiveEmail(request.getReceiveEmail());

            if (request.getPortalAccessEnabled() != null) {
                boolean wasEnabled = parent.isPortalAccessEnabled();
                parent.setPortalAccessEnabled(request.getPortalAccessEnabled());
                if (!wasEnabled && request.getPortalAccessEnabled()) {
                    provisionPortalUser(parent);
                }
            }

            Parent saved = parentRepository.save(parent);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parent updated successfully");
            response.setEntity(toResponseDTO(saved));
        } catch (Exception e) {
            log.error("Error updating parent id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating parent: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> deleteParent(Long id) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Parent parent = parentRepository.findById(id)
                    .filter(p -> p.getDeletedFlag() == 'N')
                    .orElse(null);

            if (parent == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Parent not found");
                return response;
            }

            parent.setDeletedFlag('Y');
            parentRepository.save(parent);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parent deleted successfully");
            response.setEntity(java.util.Map.of("id", id, "deleted", true));
            log.info("Soft-deleted parent id={}", id);
        } catch (Exception e) {
            log.error("Error deleting parent id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error deleting parent: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request) {
        CustomResponse<ParentResponseDTO> response = new CustomResponse<>();
        try {
            Parent parent = parentRepository.findById(id)
                    .filter(p -> p.getDeletedFlag() == 'N')
                    .orElse(null);

            if (parent == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Parent not found");
                return response;
            }

            boolean wasEnabled = parent.isPortalAccessEnabled();
            parent.setPortalAccessEnabled(request.isPortalAccessEnabled());

            if (!wasEnabled && request.isPortalAccessEnabled()) {
                provisionPortalUser(parent);
            }

            Parent saved = parentRepository.save(parent);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parent portal access updated successfully");
            response.setEntity(toResponseDTO(saved));
            log.info("Portal access for parent id={} set to {}", id, request.isPortalAccessEnabled());
        } catch (Exception e) {
            log.error("Error updating portal access for parent id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating portal access: " + e.getMessage());
        }
        return response;
    }

    private ParentResponseDTO toResponseDTO(Parent p) {
        return ParentResponseDTO.builder()
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
                .userId(p.getUser() != null ? p.getUser().getId() : null)
                .build();
    }

    private String buildFullName(String firstName, String otherNames, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null) sb.append(firstName.trim());
        if (otherNames != null && !otherNames.trim().isEmpty()) sb.append(" ").append(otherNames.trim());
        if (lastName != null) sb.append(" ").append(lastName.trim());
        return sb.toString().trim();
    }
}
