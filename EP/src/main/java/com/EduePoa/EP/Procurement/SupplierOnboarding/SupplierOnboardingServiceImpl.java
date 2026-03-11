package com.EduePoa.EP.Procurement.SupplierOnboarding;


import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Status;
import com.EduePoa.EP.Authentication.Enum.SupplierStatus;
import com.EduePoa.EP.Authentication.Role.Role;
import com.EduePoa.EP.Authentication.Role.RoleRepository;
import com.EduePoa.EP.Authentication.User.User;
import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Procurement.SupplierOnboarding.Requests.SupplierOnboardingRequestDTO;
import com.EduePoa.EP.Procurement.SupplierOnboarding.Responses.SupplierOnboardingResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class SupplierOnboardingServiceImpl implements SupplierOnboardingService {

    private final SupplierOnboardingRepository supplierRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

//    @Transactional
    public CustomResponse<?> registerSupplier(SupplierOnboardingRequestDTO request) {
        CustomResponse<SupplierOnboarding> response = new CustomResponse<>();

        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("Email '" + request.getEmail() + "' is already registered.");
                return response;
            }

            if (supplierRepository.existsByBusinessEmail(request.getBusinessEmail())) {

                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("Business email '" + request.getBusinessEmail() + "' is already registered.");
                return response;
            }

            if (supplierRepository.existsByBusinessRegistrationNumber(request.getBusinessRegistrationNumber())) {
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("Business registration number '" + request.getBusinessRegistrationNumber()
                        + "' already exists.");
                return response;
            }

            Role supplierRole = roleRepository.findByName("SUPPLIER")
                    .orElseThrow(() -> new IllegalStateException(
                            "Role 'SUPPLIER' not found. Please seed the roles table."));

            String temporaryPassword ="1234";

            User user = new User();
            user.setUsername(request.getEmail());
            user.setPassword(passwordEncoder.encode(temporaryPassword));
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setIdNumber(request.getIdNumber());
            user.setRole(supplierRole);
            user.setStatus(Status.ACTIVE);
            user.setEnabledFlag('Y');
            user.setDeletedFlag('N');
            user.setForcePasswordReset(true);
//            user.setFailedLoginAttempts(0);

            User savedUser = userRepository.save(user);
            log.info("Supplier user account created: {}", savedUser.getEmail());
            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();
            System.out.println("this is my userName"+ email);

            User createdBy = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            SupplierOnboarding supplier = getSupplierOnboarding(request, savedUser,createdBy);

            SupplierOnboarding savedSupplier = supplierRepository.save(supplier);
            log.info("Supplier profile created for: {}", savedSupplier.getBusinessName());

            sendOnboardingEmail(savedUser, savedSupplier, temporaryPassword);

            response.setStatusCode(HttpStatus.CREATED.value());
            response.setMessage(
                    "Supplier registered successfully. A confirmation email has been sent to " + request.getEmail());
            response.setEntity(savedSupplier);
//            auditService.logAction("POST", "SUPPLIER ONBOARDING", savedSupplier, "Supplier registered: " + savedSupplier.getBusinessName());

        } catch (IllegalStateException ex) {
            log.error("Configuration error during supplier registration: {}", ex.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(ex.getMessage());
            response.setEntity(null);

        } catch (Exception ex) {
            log.error("Unexpected error during supplier registration: {}", ex.getMessage(), ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred. Please try again.");
            response.setEntity(null);
        }

        return response;
    }

    @NotNull
    private static SupplierOnboarding getSupplierOnboarding(SupplierOnboardingRequestDTO request, User savedUser ,User createdBy ) {
        SupplierOnboarding supplier = new SupplierOnboarding();
        supplier.setBusinessName(request.getBusinessName());
        supplier.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber());
        supplier.setBusinessType(request.getBusinessType());
        supplier.setIndustry(request.getIndustry());
        supplier.setTaxPinNumber(request.getTaxPinNumber());
        supplier.setRegistrationDate(request.getRegistrationDate());
        supplier.setBusinessEmail(request.getBusinessEmail());
        supplier.setBusinessPhone(request.getBusinessPhone());
        supplier.setBusinessAddress(request.getBusinessAddress());
        supplier.setCounty(request.getCounty());
        supplier.setCountry(request.getCountry());
        supplier.setWebsite(request.getWebsite());
        supplier.setCreatedBy(createdBy);
        supplier.setCreatedDate(LocalDateTime.now());

        supplier.setBankBranch(request.getBankBranch());
        supplier.setAccountName(request.getAccountName());
        supplier.setAccountNumber(request.getAccountNumber());
        supplier.setBusinessCertificate(request.getBusinessCertificate());
        supplier.setKraPin(request.getKraPin());
        // supplier.setSupplierStatus(SupplierStatus.PENDING);
        supplier.setUser(savedUser);
        return supplier;
    }

    @Transactional(readOnly = true)
    public CustomResponse<SupplierOnboardingResponseDTO> getSupplierById(Long id) {

        CustomResponse<SupplierOnboardingResponseDTO> response = new CustomResponse<>();

        try {
            SupplierOnboarding supplier = supplierRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Supplier not found."));

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Supplier retrieved successfully.");
            response.setEntity(mapToResponseDTO(supplier));
//            auditService.logAction("GET", "SUPPLIER ONBOARDING", supplier, "Supplier retrieved: " + supplier.getBusinessName());

        } catch (RuntimeException ex) {

            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(ex.getMessage());

        } catch (Exception ex) {
            log.error("Unexpected error: {}", ex.getMessage(), ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred.");
        }

        return response;
    }

    @Transactional(readOnly = true)
    public CustomResponse<List<SupplierOnboardingResponseDTO>> getAllSuppliers() {
        CustomResponse<List<SupplierOnboardingResponseDTO>> response = new CustomResponse<>();
        try {
            List<SupplierOnboarding> suppliers = supplierRepository.findAll();

            List<SupplierOnboardingResponseDTO> dtoList = suppliers.stream()
                    .map(this::mapToResponseDTO)
                    .toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Suppliers retrieved successfully.");
            response.setEntity(dtoList);
//            auditService.logAction("GET", "SUPPLIER ONBOARDING", null, "All suppliers retrieved, count: " + dtoList.size());

        } catch (Exception ex) {
            log.error("Error retrieving suppliers: {}", ex.getMessage(), ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An error occurred.");
        }

        return response;
    }

    @Transactional
    public CustomResponse<?> updateSupplier(Long id, SupplierOnboardingRequestDTO request) {

        CustomResponse<Object> response = new CustomResponse<>();

        try {
            SupplierOnboarding supplier = supplierRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Supplier not found."));

            // Check email uniqueness
            if (!supplier.getBusinessEmail().equals(request.getBusinessEmail()) &&
                    supplierRepository.existsByBusinessEmail(request.getBusinessEmail())) {

                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setMessage("Business email already exists.");
                return response;
            }

            supplier.setBusinessName(request.getBusinessName());
            supplier.setBusinessType(request.getBusinessType());
            supplier.setIndustry(request.getIndustry());
            supplier.setBusinessEmail(request.getBusinessEmail());
            supplier.setBusinessPhone(request.getBusinessPhone());
            supplier.setBusinessAddress(request.getBusinessAddress());
            supplier.setCounty(request.getCounty());
            supplier.setCountry(request.getCountry());

            SupplierOnboarding updated = supplierRepository.save(supplier);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Supplier updated successfully.");
            response.setEntity(mapToResponseDTO(updated));
//            auditService.logAction("PUT", "SUPPLIER ONBOARDING", updated, "Supplier updated: " + updated.getBusinessName());

            log.info("Supplier updated successfully: {}", supplier.getBusinessName());
            ;

        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage(ex.getMessage());

        } catch (Exception ex) {
            log.error("Unexpected error updating supplier: {}", ex.getMessage(), ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred.");
        }

        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> deleteSupplier(Long id) {
        CustomResponse<?> response = new CustomResponse<>();

        try {
            SupplierOnboarding supplier = supplierRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Supplier not found."));

            supplierRepository.delete(supplier);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Supplier deleted successfully.");
//            auditService.logAction("DELETE", "SUPPLIER ONBOARDING", null, "Supplier deleted: " + supplier.getBusinessName());

            log.info("Supplier deleted: {}", supplier.getBusinessName());

        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage(ex.getMessage());

        } catch (Exception ex) {
            log.error("Unexpected error deleting supplier: {}", ex.getMessage(), ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred.");
        }

        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> approveSupplier(Long id) {
        CustomResponse<SupplierOnboardingResponseDTO> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Supplier not found."));

            // Check if already approved
            if (supplier.getStatus() == SupplierStatus.APPROVED) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Supplier is already approved.");
                return response;
            }
            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();
            System.out.println("this is my userName"+ email);

            User ApprovedBy = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            // Update status to APPROVED
            supplier.setStatus(SupplierStatus.APPROVED);
            supplier.setRejectionReason(null);
            supplier.setApprovedBy(ApprovedBy);
            supplier.setApprovedAt(LocalDateTime.now());

            SupplierOnboarding updated = supplierRepository.save(supplier);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Supplier approved successfully.");
            response.setEntity(mapToResponseDTO(updated));
//            auditService.logAction("APPROVE", "SUPPLIER ONBOARDING", updated, "Supplier approved: " + updated.getBusinessName());

            // Send approval email
            sendApprovalEmail(supplier);

            log.info("Supplier approved: {}", supplier.getBusinessName());

        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage(ex.getMessage());
            response.setEntity(null);

        } catch (Exception e) {
            log.error("Error approving supplier: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred.");
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Transactional
    public CustomResponse<?> rejectSupplier(Long id, String rejectionReason) {
        CustomResponse<SupplierOnboardingResponseDTO> response = new CustomResponse<>();
        try {
            SupplierOnboarding supplier = supplierRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Supplier not found."));

            // Check if already rejected
            if (supplier.getStatus() == SupplierStatus.REJECTED) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Supplier is already rejected.");
                return response;
            }

            // Validate rejection reason
            if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setMessage("Rejection reason is required.");
                return response;
            }
            String email = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();
            System.out.println("this is my userName"+ email);

            User rejectedBy = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));

            // Update status to REJECTED
            supplier.setStatus(SupplierStatus.REJECTED);
            supplier.setRejectionReason(rejectionReason);
            supplier.setRejectedBy(rejectedBy);
            supplier.setRejectedAt(LocalDateTime.now());

            SupplierOnboarding updated = supplierRepository.save(supplier);

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Supplier rejected successfully.");
            response.setEntity(mapToResponseDTO(updated));
//            auditService.logAction("REJECT", "SUPPLIER ONBOARDING", updated, "Supplier rejected: " + updated.getBusinessName() + ", reason: " + rejectionReason);

            // Send rejection email
            sendRejectionEmail(supplier, rejectionReason);

            log.info("Supplier rejected: {}. Reason: {}",
                    supplier.getBusinessName(), rejectionReason);

        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
            response.setMessage(ex.getMessage());
            response.setEntity(null);

        } catch (Exception e) {
            log.error("Error rejecting supplier: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("An unexpected error occurred.");
            response.setEntity(null);
        }
        return response;
    }

    private SupplierOnboardingResponseDTO mapToResponseDTO(SupplierOnboarding supplier) {
        SupplierOnboardingResponseDTO dto = new SupplierOnboardingResponseDTO();
        dto.setId(supplier.getId());
        dto.setBusinessName(supplier.getBusinessName());
        dto.setBusinessRegistrationNumber(supplier.getBusinessRegistrationNumber());
        dto.setBusinessType(supplier.getBusinessType());
        dto.setIndustry(supplier.getIndustry());
        dto.setBusinessEmail(supplier.getBusinessEmail());
        dto.setBusinessPhone(supplier.getBusinessPhone());
        dto.setCounty(supplier.getCounty());
        dto.setCountry(supplier.getCountry());
        dto.setStatus(supplier.getStatus());

        // if (supplier.getUser() != null) {
        // dto.setContactPerson(
        // supplier.getUser().getFirstName() + " " +
        // supplier.getUser().getLastName());
        // }

        return dto;
    }

    private void sendOnboardingEmail(User user, SupplierOnboarding supplier, String temporaryPassword) {
        try {
            String subject = "Welcome to the School Fee Management Platform – Supplier Registration Successful";

            String body = buildOnboardingEmailBody(user, supplier, temporaryPassword);

//            EmailRequestDTO emailRequest = new EmailRequestDTO();
//            emailRequest.setTo(user.getEmail());
//            emailRequest.setSubject(subject);
//            emailRequest.setBody(body);
//            emailRequest.setHtml(true);
//
//            ncbaEmailService.sendEmail(emailRequest);
            log.info("Onboarding email sent to: {}", user.getEmail());

        } catch (Exception ex) {
            // Email failure must never roll back the registration transaction
            log.error("Failed to send onboarding email to {}: {}", user.getEmail(), ex.getMessage(), ex);
        }
    }

    private String buildOnboardingEmailBody(User user, SupplierOnboarding supplier, String temporaryPassword) {
        return "<html><body style='font-family: Arial, sans-serif; color: #333;'>"
                + "<h2 style='color: #2E75B6;'>Supplier Registration Successful</h2>"
                + "<p>Dear <strong>" + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                + "<p>Thank you for registering <strong>" + supplier.getBusinessName() + "</strong> "
                + "on the School Fee Management Platform. Your supplier profile has been received "
                + "and is currently under review.</p>"

                + "<h3 style='color: #2E75B6;'>Your Login Credentials</h3>"
                + "<table style='border-collapse: collapse; width: 400px;'>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Username</strong></td>"
                + "<td style='padding: 8px;'>" + user.getUsername() + "</td></tr>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Temporary Password</strong></td>"
                + "<td style='padding: 8px;'>" + temporaryPassword + "</td></tr>"
                + "</table>"
                + "<p style='color: #c0392b;'><strong>Important:</strong> You will be required to change "
                + "this password when you log in for the first time.</p>"

                + "<h3 style='color: #2E75B6;'>Registration Summary</h3>"
                + "<table style='border-collapse: collapse; width: 400px;'>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Business Name</strong></td>"
                + "<td style='padding: 8px;'>" + supplier.getBusinessName() + "</td></tr>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Registration No.</strong></td>"
                + "<td style='padding: 8px;'>" + supplier.getBusinessRegistrationNumber() + "</td></tr>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Business Email</strong></td>"
                + "<td style='padding: 8px;'>" + supplier.getBusinessEmail() + "</td></tr>"
                + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Status</strong></td>"
                + "<td style='padding: 8px;'><span style='color: #e67e22;'>PENDING REVIEW</span></td></tr>"
                + "</table>"

                + "<p>Our team will review your application and notify you once your account has been approved. "
                + "This process typically takes <strong>1–2 business days</strong>.</p>"

                + "<p>If you have any questions, please contact our support team.</p>"
                + "<br/>"
                + "<p>Best regards,<br/><strong>School Fee Management Team</strong></p>"
                + "</body></html>";
    }

    private void sendApprovalEmail(SupplierOnboarding supplier) {
        try {
            User user = supplier.getUser();
            if (user == null || user.getEmail() == null)
                return;

            String subject = "Supplier Application Approved – School Fee Management Platform";
            String body = "<html><body style='font-family: Arial, sans-serif; color: #333;'>"
                    + "<h2 style='color: #27ae60;'>Supplier Application Approved ✅</h2>"
                    + "<p>Dear <strong>" + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                    + "<p>We are pleased to inform you that your supplier application for "
                    + "<strong>" + supplier.getBusinessName()
                    + "</strong> has been <strong style='color: #27ae60;'>approved</strong>.</p>"
                    + "<h3 style='color: #2E75B6;'>What's Next?</h3>"
                    + "<ul>"
                    + "<li>You can now log in to the platform using your credentials.</li>"
                    + "<li>You may start receiving purchase orders from the school.</li>"
                    + "<li>Ensure your business details are up to date in your profile.</li>"
                    + "</ul>"
                    + "<table style='border-collapse: collapse; width: 400px; margin-top: 10px;'>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Business Name</strong></td>"
                    + "<td style='padding: 8px;'>" + supplier.getBusinessName() + "</td></tr>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Registration No.</strong></td>"
                    + "<td style='padding: 8px;'>" + supplier.getBusinessRegistrationNumber() + "</td></tr>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Status</strong></td>"
                    + "<td style='padding: 8px;'><span style='color: #27ae60;'><strong>APPROVED</strong></span></td></tr>"
                    + "</table>"
                    + "<p>If you have any questions, please contact our support team.</p>"
                    + "<br/><p>Best regards,<br/><strong>School Fee Management Team</strong></p>"
                    + "</body></html>";

//            EmailRequestDTO emailRequest = new EmailRequestDTO();
//            emailRequest.setTo(user.getEmail());
//            emailRequest.setSubject(subject);
//            emailRequest.setBody(body);
//            emailRequest.setHtml(true);
//
//            ncbaEmailService.sendEmail(emailRequest);
            log.info("Approval email sent to: {}", user.getEmail());

        } catch (Exception ex) {
            log.error("Failed to send approval email to supplier {}: {}", supplier.getBusinessName(), ex.getMessage(),
                    ex);
        }
    }

    private void sendRejectionEmail(SupplierOnboarding supplier, String rejectionReason) {
        try {
            User user = supplier.getUser();
            if (user == null || user.getEmail() == null)
                return;

            String subject = "Supplier Application Rejected – School Fee Management Platform";
            String body = "<html><body style='font-family: Arial, sans-serif; color: #333;'>"
                    + "<h2 style='color: #c0392b;'>Supplier Application Rejected</h2>"
                    + "<p>Dear <strong>" + user.getFirstName() + " " + user.getLastName() + "</strong>,</p>"
                    + "<p>We regret to inform you that your supplier application for "
                    + "<strong>" + supplier.getBusinessName()
                    + "</strong> has been <strong style='color: #c0392b;'>rejected</strong>.</p>"
                    + "<h3 style='color: #2E75B6;'>Reason for Rejection</h3>"
                    + "<div style='background-color: #fdf2f2; border-left: 4px solid #c0392b; padding: 12px; margin: 10px 0;'>"
                    + "<p style='margin: 0;'>" + rejectionReason + "</p>"
                    + "</div>"
                    + "<table style='border-collapse: collapse; width: 400px; margin-top: 10px;'>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Business Name</strong></td>"
                    + "<td style='padding: 8px;'>" + supplier.getBusinessName() + "</td></tr>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Registration No.</strong></td>"
                    + "<td style='padding: 8px;'>" + supplier.getBusinessRegistrationNumber() + "</td></tr>"
                    + "<tr><td style='padding: 8px; background-color: #f2f2f2;'><strong>Status</strong></td>"
                    + "<td style='padding: 8px;'><span style='color: #c0392b;'><strong>REJECTED</strong></span></td></tr>"
                    + "</table>"
                    + "<p>If you believe this was made in error or would like to resubmit your application, "
                    + "please contact our support team for further guidance.</p>"
                    + "<br/><p>Best regards,<br/><strong>School Fee Management Team</strong></p>"
                    + "</body></html>";

//            EmailRequestDTO emailRequest = new EmailRequestDTO();
//            emailRequest.setTo(user.getEmail());
//            emailRequest.setSubject(subject);
//            emailRequest.setBody(body);
//            emailRequest.setHtml(true);
//
//            ncbaEmailService.sendEmail(emailRequest);
            log.info("Rejection email sent to: {}", user.getEmail());

        } catch (Exception ex) {
            log.error("Failed to send rejection email to supplier {}: {}", supplier.getBusinessName(), ex.getMessage(),
                    ex);
        }
    }
}
