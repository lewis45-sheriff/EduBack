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
import com.EduePoa.EP.Parents.Response.ParentStudentDTO;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentGuardian;
import com.EduePoa.EP.StudentRegistration.StudentGuardianRepository;
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
    private final StudentGuardianRepository studentGuardianRepository;

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
    public CustomResponse<?> getParentStudents(Long parentId) {
        CustomResponse<List<ParentStudentDTO>> response = new CustomResponse<>();
        try {
            // SECURITY: ignore the supplied parentId and derive the parent from the authenticated
            // user. This prevents a logged-in parent from reading another parent's children by
            // changing the path id. The frontend may keep sending an id; it has no effect.
            Parent parent = resolveCurrentParent();
            if (parent == null) {
                response.setStatusCode(HttpStatus.FORBIDDEN.value());
                response.setMessage("No parent profile is linked to the current user");
                response.setEntity(null);
                return response;
            }
            Long resolvedParentId = parent.getId();

            // The parent's children, derived from the student_guardian links. Tenant-isolated via
            // the Hibernate filter; the student graph is eagerly fetched to avoid N+1. Each DTO
            // carries that student's identity plus THIS parent's guardian attributes.
            List<ParentStudentDTO> students = studentGuardianRepository.findByParent_Id(resolvedParentId).stream()
                    .filter(g -> g.getStudent() != null)
                    .filter(g -> g.getStudent().getIsDeleted() == null || !g.getStudent().getIsDeleted())
                    .map(g -> {
                        Student s = g.getStudent();
                        return ParentStudentDTO.builder()
                                .studentId(s.getId())
                                .admissionNumber(s.getAdmissionNumber())
                                .fullName(fullName(s))
                                .gradeName(s.getGradeName())
                                .streamName(s.getStreamName())
                                .gender(s.getGender())
                                .relationship(g.getRelationship())
                                .primaryContact(g.isPrimaryContact())
                                .feePayer(g.isFeePayer())
                                .build();
                    })
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Parent's students retrieved successfully (" + students.size() + " student(s))");
            response.setEntity(students);
        } catch (Exception e) {
            log.error("Error fetching students for the current parent: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching parent's students: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    /**
     * Resolves the {@link Parent} linked to the currently authenticated user, or {@code null} if
     * there is no authenticated user or no parent profile is linked to it. The authenticated
     * principal is a {@link User}; the parent is resolved via its {@code user_id} link.
     */
    private Parent resolveCurrentParent() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        Long userId = null;
        String email = null;
        if (principal instanceof User u) {
            userId = u.getId();
            email = u.getEmail();
        } else {
            // Fallback: principal name is the username (email) in this app.
            email = auth.getName();
        }

        Parent parent = null;
        if (userId != null) {
            parent = parentRepository.findByUser_Id(userId).orElse(null);
        }
        if (parent == null && email != null) {
            // Fallback for accounts where the parent↔user link isn't populated but the
            // parent record shares the login email.
            parent = parentRepository.findByEmail(email).orElse(null);
        }
        if (parent != null && parent.getDeletedFlag() != 'N') {
            return null;
        }
        return parent;
    }

    private static String fullName(Student s) {
        String first = s.getFirstName() != null ? s.getFirstName() : "";
        String last = s.getLastName() != null ? s.getLastName() : "";
        return (first + " " + last).trim();
    }

    @Override
    public Long getCurrentParentId() {
        Parent parent = resolveCurrentParent();
        return parent != null ? parent.getId() : null;
    }

    @Override
    public boolean currentParentOwnsStudent(Long studentId) {
        if (studentId == null) {
            return false;
        }
        Parent parent = resolveCurrentParent();
        if (parent == null) {
            return false;
        }
        // The guardian repository is tenant-aware, so a link in another tenant will not match.
        return studentGuardianRepository
                .findByStudent_IdAndParent_Id(studentId, parent.getId())
                .isPresent();
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
