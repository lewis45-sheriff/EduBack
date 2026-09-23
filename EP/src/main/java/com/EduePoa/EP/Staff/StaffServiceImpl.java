package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Authentication.Email.EmailService;
import com.EduePoa.EP.Authentication.Enum.Permissions;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Staff.Enum.StaffType;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Grade.Stream.GradeStreamRepository;
import com.EduePoa.EP.Staff.Request.CreateStaffRequestDTO;
import com.EduePoa.EP.Staff.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Staff.Request.StaffInfoDTO;
import com.EduePoa.EP.Staff.Request.UpdateStaffRequestDTO;
import com.EduePoa.EP.Staff.Response.ClassAssignmentDTO;
import com.EduePoa.EP.Staff.Response.MyAssignmentDTO;
import com.EduePoa.EP.Staff.Response.StaffResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffServiceImpl implements StaffService {

    private static final String TEACHER_ROLE_NAME = "ROLE_TEACHER";
    private static final String STAFF_ROLE_NAME   = "ROLE_STAFF";
    private static final String TEMP_PASSWORD      = "Staff@1234";

    private final StaffRepository       staffRepository;
    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;
    private final EmailService          emailService;
    private final GradeStreamRepository gradeStreamRepository;
    private final GradeRepository       gradeRepository;

    @Override
    public CustomResponse<?> createStaff(CreateStaffRequestDTO request) {
        CustomResponse<StaffResponseDTO> response = new CustomResponse<>();
        try {
            StaffInfoDTO dto = request.resolve();

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
            if (dto.getStaffType() == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Staff type is required");
                return response;
            }
            // Teachers are system users, so an email is mandatory for them (used as login).
            if (dto.getStaffType() == StaffType.TEACHER
                    && (dto.getEmail() == null || dto.getEmail().isBlank())) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Email is required for teachers (used as their login username)");
                return response;
            }

            if (staffRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that phone number already exists");
                return response;
            }
            if (dto.getEmail() != null && !dto.getEmail().isBlank()
                    && staffRepository.existsByEmail(dto.getEmail())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that email already exists");
                return response;
            }
            if (dto.getEmployeeNumber() != null && !dto.getEmployeeNumber().isBlank()
                    && staffRepository.existsByEmployeeNumber(dto.getEmployeeNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that employee number already exists");
                return response;
            }

            Staff staff = toEntity(dto);
            Staff saved = staffRepository.save(staff);

            // Teachers always get a login; other staff only when portal access is requested.
            Long userId = null;
            if (saved.getStaffType() == StaffType.TEACHER || saved.isPortalAccessEnabled()) {
                userId = provisionPortalUser(saved);
            }

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage("Staff member created successfully");
            response.setEntity(toResponseDTO(saved));
            log.info("Created staff id={} type={} userId={}", saved.getId(), saved.getStaffType(), userId);

        } catch (Exception e) {
            log.error("Error creating staff: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error creating staff: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllStaff() {
        CustomResponse<List<StaffResponseDTO>> response = new CustomResponse<>();
        try {
            List<StaffResponseDTO> staff = staffRepository.findAll()
                    .stream()
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Staff fetched successfully");
            response.setEntity(staff);
            log.info("Fetched {} staff members", staff.size());
        } catch (Exception e) {
            log.error("Error fetching staff: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching staff: " + e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getStaffById(Long id) {
        CustomResponse<StaffResponseDTO> response = new CustomResponse<>();
        try {
            Staff staff = staffRepository.findById(id)
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);

            if (staff == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Staff member not found");
                return response;
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Staff member fetched successfully");
            response.setEntity(toResponseDTO(staff));
        } catch (Exception e) {
            log.error("Error fetching staff id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error fetching staff: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> updateStaff(Long id, UpdateStaffRequestDTO request) {
        CustomResponse<StaffResponseDTO> response = new CustomResponse<>();
        try {
            Staff staff = staffRepository.findById(id)
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);

            if (staff == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Staff member not found");
                return response;
            }

            if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                    && !request.getPhoneNumber().equals(staff.getPhoneNumber())
                    && staffRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that phone number already exists");
                return response;
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()
                    && !request.getEmail().equals(staff.getEmail())
                    && staffRepository.existsByEmail(request.getEmail())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that email already exists");
                return response;
            }
            if (request.getEmployeeNumber() != null && !request.getEmployeeNumber().isBlank()
                    && !request.getEmployeeNumber().equals(staff.getEmployeeNumber())
                    && staffRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("A staff member with that employee number already exists");
                return response;
            }

            if (request.getFirstName()            != null) staff.setFirstName(request.getFirstName().trim());
            if (request.getLastName()             != null) staff.setLastName(request.getLastName().trim());
            if (request.getOtherNames()           != null) staff.setOtherNames(request.getOtherNames());
            if (request.getEmployeeNumber()       != null) staff.setEmployeeNumber(request.getEmployeeNumber().trim());
            if (request.getPhoneNumber()          != null) staff.setPhoneNumber(request.getPhoneNumber().trim());
            if (request.getAlternatePhoneNumber() != null) staff.setAlternatePhoneNumber(request.getAlternatePhoneNumber());
            if (request.getEmail()                != null) staff.setEmail(request.getEmail());
            if (request.getNationalIdOrPassport() != null) staff.setNationalIdOrPassport(request.getNationalIdOrPassport());
            if (request.getGender()               != null) staff.setGender(request.getGender());
            if (request.getAddress()              != null) staff.setAddress(request.getAddress());
            if (request.getStaffType()            != null) staff.setStaffType(request.getStaffType());
            if (request.getDepartment()           != null) staff.setDepartment(request.getDepartment());
            if (request.getDesignation()          != null) staff.setDesignation(request.getDesignation());
            if (request.getDateOfEmployment()     != null) staff.setDateOfEmployment(request.getDateOfEmployment());
            if (request.getReceiveSms()           != null) staff.setReceiveSms(request.getReceiveSms());
            if (request.getReceiveEmail()         != null) staff.setReceiveEmail(request.getReceiveEmail());

            if (request.getPortalAccessEnabled() != null) {
                boolean wasEnabled = staff.isPortalAccessEnabled();
                staff.setPortalAccessEnabled(request.getPortalAccessEnabled());
                if (!wasEnabled && request.getPortalAccessEnabled()) {
                    provisionPortalUser(staff);
                }
            }

            // A staff member whose type is (or becomes) TEACHER must have a login.
            if (staff.getStaffType() == StaffType.TEACHER && staff.getUser() == null) {
                provisionPortalUser(staff);
            }

            Staff saved = staffRepository.save(staff);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Staff member updated successfully");
            response.setEntity(toResponseDTO(saved));
        } catch (Exception e) {
            log.error("Error updating staff id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating staff: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> deleteStaff(Long id) {
        CustomResponse<Object> response = new CustomResponse<>();
        try {
            Staff staff = staffRepository.findById(id)
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);

            if (staff == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Staff member not found");
                return response;
            }

            staff.setDeletedFlag('Y');
            staffRepository.save(staff);

            // Deactivate the linked login so the staff member can no longer sign in.
            User user = staff.getUser();
            if (user != null) {
                user.setEnabledFlag('N');
                user.setStatus(Status.DEACTIVATED);
                userRepository.save(user);
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Staff member deleted successfully");
            response.setEntity(java.util.Map.of("id", id, "deleted", true));
            log.info("Soft-deleted staff id={}", id);
        } catch (Exception e) {
            log.error("Error deleting staff id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error deleting staff: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> updatePortalAccess(Long id, PortalAccessRequestDTO request) {
        CustomResponse<StaffResponseDTO> response = new CustomResponse<>();
        try {
            Staff staff = staffRepository.findById(id)
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);

            if (staff == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Staff member not found");
                return response;
            }

            // Teachers must always retain a login and cannot have it revoked here.
            if (staff.getStaffType() == StaffType.TEACHER && !request.isPortalAccessEnabled()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Teachers are system users and cannot have portal access disabled");
                return response;
            }

            boolean wasEnabled = staff.isPortalAccessEnabled();
            staff.setPortalAccessEnabled(request.isPortalAccessEnabled());

            if (!wasEnabled && request.isPortalAccessEnabled()) {
                provisionPortalUser(staff);
            } else if (wasEnabled && !request.isPortalAccessEnabled() && staff.getUser() != null) {
                User user = staff.getUser();
                user.setEnabledFlag('N');
                user.setStatus(Status.INACTIVE);
                userRepository.save(user);
            }

            Staff saved = staffRepository.save(staff);
            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Staff portal access updated successfully");
            response.setEntity(toResponseDTO(saved));
            log.info("Portal access for staff id={} set to {}", id, request.isPortalAccessEnabled());
        } catch (Exception e) {
            log.error("Error updating portal access for staff id={}: {}", id, e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error updating portal access: " + e.getMessage());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getMyAssignment() {
        CustomResponse<MyAssignmentDTO> response = new CustomResponse<>();
        try {
            // Resolve the current user from the JWT-populated security context.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (auth == null || auth.getName() == null)
                    ? null
                    : userRepository.findByEmail(auth.getName()).orElse(null);

            if (currentUser == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED.value());
                response.setMessage("Not authenticated");
                return response;
            }

            // Find the staff profile linked to this user (tenant-scoped via the repo).
            Staff staff = staffRepository.findByUserId(currentUser.getId())
                    .filter(s -> s.getDeletedFlag() == 'N')
                    .orElse(null);

            if (staff == null) {
                response.setStatusCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("No staff profile found for the current user");
                return response;
            }

            // Determine the class this staff member is the class teacher of.
            // Streams take precedence over unstreamed grades.
            ClassAssignmentDTO assignment = null;
            GradeStream stream = gradeStreamRepository.findByClassTeacherId(staff.getId()).orElse(null);
            if (stream != null) {
                Grade grade = stream.getGrade();
                Long gradeId = grade != null ? grade.getId() : null;
                String gradeName = grade != null ? grade.getName() : null;
                assignment = ClassAssignmentDTO.builder()
                        .type("stream")
                        .id(stream.getId())
                        .gradeId(gradeId)
                        .gradeName(gradeName)
                        .streamName(stream.getName())
                        .label(buildLabel(gradeName, stream.getName()))
                        .build();
            } else {
                Grade grade = gradeRepository.findByClassTeacherId(staff.getId()).orElse(null);
                if (grade != null) {
                    assignment = ClassAssignmentDTO.builder()
                            .type("grade")
                            .id(grade.getId())
                            .gradeId(grade.getId())
                            .gradeName(grade.getName())
                            .streamName(null)
                            .label(buildLabel(grade.getName(), null))
                            .build();
                }
            }

            MyAssignmentDTO dto = MyAssignmentDTO.builder()
                    .staffId(staff.getId())
                    .userId(currentUser.getId())
                    .fullName(buildFullName(staff.getFirstName(), staff.getOtherNames(), staff.getLastName()))
                    .employeeNumber(staff.getEmployeeNumber())
                    .staffType(staff.getStaffType())
                    .assignment(assignment)
                    .build();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage(assignment != null ? "Assignment retrieved successfully" : "No class assignment found");
            response.setEntity(dto);
            log.info("Resolved my-assignment for staffId={} (userId={}): {}",
                    staff.getId(), currentUser.getId(), assignment != null ? assignment.getLabel() : "none");

        } catch (Exception e) {
            log.error("Error resolving current staff assignment: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Error resolving assignment: " + e.getMessage());
        }
        return response;
    }

    private String buildLabel(String gradeName, String streamName) {
        String g = gradeName != null ? gradeName.trim() : "";
        if (streamName != null && !streamName.trim().isEmpty()) {
            return (g + " — " + streamName.trim()).trim();
        }
        return g;
    }

    /**
     * Creates (or links) a system login for the staff member. Teachers receive the
     * {@code ROLE_TEACHER} role; all other staff receive {@code ROLE_STAFF}. The role
     * is created on demand if it does not yet exist for the tenant.
     */
    private Long provisionPortalUser(Staff staff) {
        String email = staff.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Cannot provision login for staff id={}: no email on record", staff.getId());
            return null;
        }

        if (userRepository.existsByEmail(email)) {
            Long existingId = userRepository.findByEmail(email).map(User::getId).orElse(null);
            if (staff.getUser() == null && existingId != null) {
                userRepository.findByEmail(email).ifPresent(staff::setUser);
                staffRepository.save(staff);
            }
            return existingId;
        }

        String roleName = staff.getStaffType() == StaffType.TEACHER ? TEACHER_ROLE_NAME : STAFF_ROLE_NAME;
        Role role = resolveRole(roleName, staff.getStaffType());

        User user = User.builder()
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .email(email)
                .username(email)
                .phoneNumber(staff.getPhoneNumber())
                .password(passwordEncoder.encode(TEMP_PASSWORD))
                .role(role)
                .status(Status.ACTIVE)
                .enabledFlag('Y')
                .deletedFlag('N')
                .is_lockedFlag('N')
                .forcePasswordReset(true)
                .passwordReset(true)
                .build();

        User savedUser = userRepository.save(user);

        staff.setPortalAccessEnabled(true);
        staff.setUser(savedUser);
        staffRepository.save(staff);
        sendWelcomeEmail(staff, email);

        return savedUser.getId();
    }

    private Role resolveRole(String roleName, StaffType staffType) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role role = new Role();
            role.setName(roleName);
            role.setStatus(Status.ACTIVE);
            if (staffType == StaffType.TEACHER) {
                role.addPermission(Permissions.CLASS_READ);
                role.addPermission(Permissions.EXAM_READ);
                role.addPermission(Permissions.EXAM_MARK_ENTER);
                role.addPermission(Permissions.ATTENDANCE_MARK);
                role.addPermission(Permissions.ATTENDANCE_READ);
                role.addPermission(Permissions.STUDENT_READ);
            } else {
                role.addPermission(Permissions.STAFF_READ);
            }
            return roleRepository.save(role);
        });
    }

    private void sendWelcomeEmail(Staff staff, String email) {
        try {
            String fullName = buildFullName(staff.getFirstName(), staff.getOtherNames(), staff.getLastName());
            String subject  = "Welcome to the Staff Portal";
            String body     = "<p>Dear " + fullName + ",</p>"
                    + "<p>Your staff account has been created. Use the credentials below to log in:</p>"
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

    private Staff toEntity(StaffInfoDTO dto) {
        Staff staff = new Staff();
        staff.setFirstName(dto.getFirstName().trim());
        staff.setLastName(dto.getLastName().trim());
        staff.setOtherNames(dto.getOtherNames());
        staff.setEmployeeNumber(dto.getEmployeeNumber());
        staff.setPhoneNumber(dto.getPhoneNumber().trim());
        staff.setAlternatePhoneNumber(dto.getAlternatePhoneNumber());
        staff.setEmail(dto.getEmail());
        staff.setNationalIdOrPassport(dto.getNationalIdOrPassport());
        staff.setGender(dto.getGender());
        staff.setAddress(dto.getAddress());
        staff.setStaffType(dto.getStaffType());
        staff.setDepartment(dto.getDepartment());
        staff.setDesignation(dto.getDesignation());
        staff.setDateOfEmployment(dto.getDateOfEmployment());
        // Teachers are always portal users regardless of the requested flag.
        staff.setPortalAccessEnabled(dto.getStaffType() == StaffType.TEACHER || dto.isPortalAccessEnabled());
        staff.setReceiveSms(dto.isReceiveSms());
        staff.setReceiveEmail(dto.isReceiveEmail());
        staff.setDeletedFlag('N');
        return staff;
    }

    private StaffResponseDTO toResponseDTO(Staff s) {
        return StaffResponseDTO.builder()
                .id(s.getId())
                .firstName(s.getFirstName())
                .lastName(s.getLastName())
                .otherNames(s.getOtherNames())
                .fullName(buildFullName(s.getFirstName(), s.getOtherNames(), s.getLastName()))
                .employeeNumber(s.getEmployeeNumber())
                .phoneNumber(s.getPhoneNumber())
                .alternatePhoneNumber(s.getAlternatePhoneNumber())
                .email(s.getEmail())
                .nationalIdOrPassport(s.getNationalIdOrPassport())
                .gender(s.getGender())
                .address(s.getAddress())
                .staffType(s.getStaffType())
                .department(s.getDepartment())
                .designation(s.getDesignation())
                .dateOfEmployment(s.getDateOfEmployment())
                .portalAccessEnabled(s.isPortalAccessEnabled())
                .userId(s.getUser() != null ? s.getUser().getId() : null)
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
