package com.EduePoa.EP.Transport;

import com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation.Audit;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.AssignTransportResponseDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.StudentTransportDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.Responses.TransportArrearsLineDTO;
import com.EduePoa.EP.Transport.Responses.TransportArrearsSummaryDTO;
import com.EduePoa.EP.Transport.Responses.TransportResponseDTO;
import com.EduePoa.EP.Transport.Responses.TransportUtilization;
import com.EduePoa.EP.Transport.Responses.TransportWithStudentsDTO;
import com.EduePoa.EP.Transport.TransportTermPrice.TransportTermPrice;
import com.EduePoa.EP.Transport.TransportTermPrice.TransportTermPriceRepository;
import com.EduePoa.EP.Transport.TransportTransactions.Requests.TransportTransactionRequestDTO;
import com.EduePoa.EP.Transport.TransportTransactions.Responses.TransportTransactionResponseDTO;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactions;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactionsRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransportServiceImpl implements TransportService {
    private final TransportRepository transportRepository;
    private final StudentRepository studentRepository;
    private final AssignTransportRepository assignTransportRepository;
    private final TransportTransactionsRepository transportTransactionsRepository;
    private final TransportTermPriceRepository transportTermPriceRepository;
    private final AuditService auditService;

    @Override
    @Audit(module = "TRANSPORT", action = "CREATE")
    public CustomResponse<?> create(TransportRequestDTO transportRequestDTO) {
        CustomResponse<TransportResponseDTO> response = new CustomResponse<>();
        try {

            if (transportRepository.existsByVehicleNumber(transportRequestDTO.getVehicleNumber())) {
                response.setMessage("Vehicle with this number already exists");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setEntity(null);
                return response;
            }

            Transport transport = Transport.builder()
                    .vehicleNumber(transportRequestDTO.getVehicleNumber())
                    .vehicleType(transportRequestDTO.getVehicleType())
                    .capacity(transportRequestDTO.getCapacity())
                    .driverName(transportRequestDTO.getDriverName())
                    .driverContact(transportRequestDTO.getDriverContact())
                    .route(transportRequestDTO.getRoute())
                    .status(transportRequestDTO.getStatus())
                    .build();

            applyTermPrices(transport, transportRequestDTO.getTermPrices());

            Transport saved = transportRepository.save(transport);

            response.setEntity(mapToResponse(saved));
            response.setMessage("Transport created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());
            auditService.log("TRANSPORT", "Created transport vehicle:", saved.getVehicleNumber(), "with ID:",
                    String.valueOf(saved.getId()));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getById(Long id) {
        CustomResponse<TransportResponseDTO> response = new CustomResponse<>();
        try {

            Transport transport = transportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transport not found"));

            response.setEntity(mapToResponse(transport));
            response.setMessage("Transport retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getByIdWithStudents(Long id) {
        CustomResponse<TransportWithStudentsDTO> response = new CustomResponse<>();
        try {
            Transport transport = transportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transport not found"));

            List<TransportWithStudentsDTO.AssignedStudent> assignedStudents =
                    assignTransportRepository.findByVehicle(transport).stream()
                            .filter(a -> a.getStudent() != null)
                            .map(a -> {
                                Student s = a.getStudent();
                                return TransportWithStudentsDTO.AssignedStudent.builder()
                                        .assignmentId(a.getId())
                                        .studentId(s.getId())
                                        .admissionNumber(s.getAdmissionNumber())
                                        .fullName(buildStudentName(s))
                                        .pickupLocation(a.getPickupLocation())
                                        .transportType(a.getTransportType())
                                        .term(a.getTerm())
                                        .year(a.getYear())
                                        .assignmentDate(a.getAssignmentDate())
                                        .build();
                            })
                            .collect(Collectors.toList());

            TransportWithStudentsDTO dto = TransportWithStudentsDTO.builder()
                    .id(transport.getId())
                    .vehicleNumber(transport.getVehicleNumber())
                    .vehicleType(transport.getVehicleType())
                    .capacity(transport.getCapacity())
                    .driverName(transport.getDriverName())
                    .driverContact(transport.getDriverContact())
                    .route(transport.getRoute())
                    .status(transport.getStatus())
                    .assignedCount(assignedStudents.size())
                    .assignedStudents(assignedStudents)
                    .build();

            response.setEntity(dto);
            response.setMessage("Transport with assigned students retrieved successfully ("
                    + assignedStudents.size() + " student(s))");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAll() {
        CustomResponse<List<TransportResponseDTO>> response = new CustomResponse<>();
        try {

            List<TransportResponseDTO> transports = transportRepository.findAll()
                    .stream()
                    .map(this::mapToResponse)
                    .toList();

            response.setEntity(transports);
            response.setMessage("Transports retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "TRANSPORT", action = "UPDATE")
    public CustomResponse<?> update(Long id, TransportRequestDTO transportRequestDTO) {
        CustomResponse<TransportResponseDTO> response = new CustomResponse<>();
        try {

            Transport transport = transportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transport not found"));

            transport.setVehicleType(transportRequestDTO.getVehicleType());
            transport.setCapacity(transportRequestDTO.getCapacity());
            transport.setDriverName(transportRequestDTO.getDriverName());
            transport.setDriverContact(transportRequestDTO.getDriverContact());
            transport.setRoute(transportRequestDTO.getRoute());
            transport.setStatus(transportRequestDTO.getStatus());

            // Replace the full set of per-term prices when provided.
            if (transportRequestDTO.getTermPrices() != null) {
                transport.getTermPrices().clear();
                applyTermPrices(transport, transportRequestDTO.getTermPrices());
            }

            Transport updated = transportRepository.save(transport);

            response.setEntity(mapToResponse(updated));
            response.setMessage("Transport updated successfully");
            response.setStatusCode(HttpStatus.OK.value());
            auditService.log("TRANSPORT", "Updated transport vehicle:", updated.getVehicleNumber(), "with ID:",
                    String.valueOf(id));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "TRANSPORT", action = "DEACTIVATE")
    public CustomResponse<?> delete(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {

            Transport transport = transportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transport not found"));

            transport.setStatus("inactive");
            transportRepository.save(transport);

            response.setMessage("Transport deactivated successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(null);
            auditService.log("TRANSPORT", "Deactivated transport vehicle with ID:", String.valueOf(id));

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "TRANSPORT", action = "ASSIGN")
    public CustomResponse<?> assign(AssignTransportRequestDTO request) {
        CustomResponse<AssignTransport> response = new CustomResponse<>();

        try {
            // Validate required per-term fields
            if (request.getTerm() == null || request.getYear() == null) {
                throw new RuntimeException("Term and year are required for a transport assignment");
            }
            if (request.getTransportType() == null) {
                throw new RuntimeException("Transport type (ONE_WAY / TWO_WAY) is required");
            }

            // Validate Student
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Prevent duplicate assignment for the same term + year
            if (assignTransportRepository.existsByStudentAndTermAndYear(student, request.getTerm(),
                    request.getYear())) {
                throw new RuntimeException("Student already has a transport assignment for "
                        + request.getTerm() + " " + request.getYear());
            }

            // Validate Vehicle
            Transport vehicle = transportRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));

            // Ensure a price exists for this vehicle/term/year so the assignment is billable
            transportTermPriceRepository
                    .findByVehicleAndTermAndYear(vehicle, request.getTerm(), request.getYear())
                    .orElseThrow(() -> new RuntimeException(
                            "No transport price configured for this vehicle in "
                                    + request.getTerm() + " " + request.getYear()));

            // Build Entity
            AssignTransport assignTransport = AssignTransport.builder()
                    .student(student)
                    .vehicle(vehicle)
                    .pickupLocation(request.getPickupLocation())
                    .transportType(request.getTransportType())
                    .term(request.getTerm())
                    .year(request.getYear())
                    .assignmentDate(LocalDate.now())
                    .build();

            // Save
            AssignTransport saved = assignTransportRepository.save(assignTransport);

            // Success Response
            response.setEntity(saved);
            response.setMessage("Transport assigned to student successfully");
            response.setStatusCode(HttpStatus.CREATED.value());
            auditService.log("TRANSPORT", "Assigned transport to student ID:", String.valueOf(request.getStudentId()),
                    "vehicle ID:", String.valueOf(request.getVehicleId()));

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        }

        return response;
    }

    @Override
    public CustomResponse<?> assignments() {
        CustomResponse<List<AssignTransportResponseDTO>> response = new CustomResponse<>();

        try {
            List<AssignTransport> assignments = assignTransportRepository.findAll();

            List<AssignTransportResponseDTO> dtoList = assignments.stream()
                    .map(at -> AssignTransportResponseDTO.builder()
                            .assignmentId(at.getId())
                            .studentId(at.getStudent().getId())
                            .studentName(
                                    at.getStudent().getFirstName()
                                            .concat(" ")
                                            .concat(at.getStudent().getLastName()))
                            .vehicleId(at.getVehicle().getId())
                            .vehiclePlateNumber(at.getVehicle().getVehicleNumber())
                            .pickupLocation(at.getPickupLocation())
                            .transportType(at.getTransportType())
                            .term(at.getTerm())
                            .year(at.getYear())
                            .admissionNumber(at.getStudent().getAdmissionNumber())
                            .assignedDate(at.getAssignmentDate())

                            .build())
                    .toList();

            response.setEntity(dtoList);
            response.setMessage("Transport assignments retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }

        return response;
    }

    @Override
    public CustomResponse<?> deleteAssignments(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> studentTransport() {
        CustomResponse<List<StudentTransportDTO>> response = new CustomResponse<>();
        try {

            List<AssignTransport> assignments = assignTransportRepository.findAll();

            List<StudentTransportDTO> data = assignments.stream().map(at -> StudentTransportDTO.builder()
                    .studentId(at.getStudent().getId())
                    .admissionNumber(at.getStudent().getAdmissionNumber())
                    .fullName(
                            at.getStudent().getFirstName() + " " +
                                    at.getStudent().getLastName())
                    .pickupLocation(at.getPickupLocation())
                    .transportType(at.getTransportType())
                    .term(at.getTerm())
                    .year(at.getYear())
                    .vehicleName(at.getVehicle().getVehicleNumber())
                    .build()).toList();

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Students with transport retrieved successfully");
            response.setEntity(data);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    @Audit(module = "TRANSPORT", action = "CREATE_TRANSACTION")
    public CustomResponse<?> createTransportTransaction(Long studentId,
            TransportTransactionRequestDTO transportTransactionRequestDTO) {
        CustomResponse<Map<String, Object>> response = new CustomResponse<>();

        try {
            log.info("Creating transaction for studentId: {}, transportId: {}",
                    studentId, transportTransactionRequestDTO.getVehicleId());

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            Transport transport = transportRepository.findById(transportTransactionRequestDTO.getVehicleId())
                    .orElseThrow(() -> new RuntimeException("Transport not found"));

            // Term and year are required to resolve the per-term price
            if (transportTransactionRequestDTO.getTerm() == null
                    || transportTransactionRequestDTO.getYear() == null) {
                response.setEntity(null);
                response.setMessage("Term and year are required to process a transport payment");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Get the expected fee based on transport type, term and year
            Double expectedFee = getExpectedFee(
                    transport,
                    transportTransactionRequestDTO.getTransportType(),
                    transportTransactionRequestDTO.getTerm(),
                    transportTransactionRequestDTO.getYear());

            if (expectedFee == null || expectedFee == 0.0) {
                response.setEntity(null);
                response.setMessage(
                        "Transport fee not configured for " + transportTransactionRequestDTO.getTransportType()
                                + " in " + transportTransactionRequestDTO.getTerm() + " "
                                + transportTransactionRequestDTO.getYear());
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Get the latest arrears from the database (most accurate)
            Double latestArrears = transportTransactionsRepository.getLatestArrears(
                    studentId,
                    transportTransactionRequestDTO.getVehicleId(),
                    transportTransactionRequestDTO.getTerm(),
                    transportTransactionRequestDTO.getYear(),
                    transportTransactionRequestDTO.getTransportType());

            // Calculate total paid before this transaction
            Double totalPaidBefore;
            if (latestArrears != null) {
                // If there are previous transactions, calculate from the latest arrears
                totalPaidBefore = expectedFee - latestArrears;
            } else {
                // No previous transactions
                totalPaidBefore = 0.0;
                latestArrears = expectedFee;
            }

            log.info("Expected Fee: {}, Latest Arrears: {}, Total Paid Before: {}, Attempted Payment: {}",
                    expectedFee, latestArrears, totalPaidBefore, transportTransactionRequestDTO.getAmount());

            // Check if already fully paid
            if (latestArrears <= 0) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("expectedFee", expectedFee);
                errorDetails.put("totalPaid", totalPaidBefore);
                errorDetails.put("totalArrears", 0.0);
                errorDetails.put("attemptedPayment", transportTransactionRequestDTO.getAmount());
                errorDetails.put("message", "Transport fee is already fully paid.");

                response.setEntity(errorDetails);
                response.setMessage("Payment rejected: Transport fee already fully paid");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Check for overpayment - reject if payment exceeds remaining balance
            if (transportTransactionRequestDTO.getAmount() > latestArrears) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("expectedFee", expectedFee);
                errorDetails.put("totalPaid", totalPaidBefore);
                errorDetails.put("totalArrears", latestArrears);
                errorDetails.put("attemptedPayment", transportTransactionRequestDTO.getAmount());
                errorDetails.put("maximumAllowedPayment", latestArrears);
                errorDetails.put("message", String.format(
                        "Payment amount (%.2f) exceeds remaining balance (%.2f). Maximum allowed payment is %.2f",
                        transportTransactionRequestDTO.getAmount(),
                        latestArrears,
                        latestArrears));

                response.setEntity(errorDetails);
                response.setMessage("Overpayment not allowed");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Validate payment amount is positive
            if (transportTransactionRequestDTO.getAmount() <= 0) {
                response.setEntity(null);
                response.setMessage("Payment amount must be greater than zero");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Calculate new totals after this payment
            Double totalPaidAfter = totalPaidBefore + transportTransactionRequestDTO.getAmount();
            Double arrearsAfter = expectedFee - totalPaidAfter;

            // Determine payment status
            String paymentStatus;
            if (Math.abs(arrearsAfter) < 0.01) { // Handle floating point precision
                paymentStatus = "COMPLETED";
                arrearsAfter = 0.0;
            } else {
                paymentStatus = "PARTIAL";
            }

            // Create and save transaction with all calculated values
            TransportTransactions transaction = getTransportTransactions(
                    transportTransactionRequestDTO,
                    student,
                    transport,
                    expectedFee,
                    totalPaidBefore,
                    totalPaidAfter,
                    arrearsAfter);

            transaction.setStatus(paymentStatus);

            log.info("Transaction before save - Student ID: {}, Transport ID: {}, Status: {}, Arrears After: {}",
                    transaction.getStudent().getId(),
                    transaction.getTransport().getId(),
                    transaction.getStatus(),
                    transaction.getArrearsAfterThis());

            TransportTransactions savedTransaction = transportTransactionsRepository.save(transaction);

            // Prepare response payload
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transaction", savedTransaction);
            responseData.put("transactionId", savedTransaction.getId());
            responseData.put("studentId", student.getId());
            responseData.put("studentName", student.getFirstName() + " " + student.getLastName());
            responseData.put("admissionNumber", student.getAdmissionNumber());
            responseData.put("vehicleNumber", transport.getVehicleNumber());
            responseData.put("route", transport.getRoute());
            responseData.put("transportType", transportTransactionRequestDTO.getTransportType());
            responseData.put("term", transportTransactionRequestDTO.getTerm());
            responseData.put("year", transportTransactionRequestDTO.getYear());
            responseData.put("expectedFee", expectedFee);
            responseData.put("currentPayment", transportTransactionRequestDTO.getAmount());
            responseData.put("totalPaidBefore", totalPaidBefore);
            responseData.put("totalPaidAfter", totalPaidAfter);
            responseData.put("arrearsAfter", arrearsAfter);
            responseData.put("paymentStatus", paymentStatus);
            responseData.put("transactionTime", savedTransaction.getTransactionTime());

            String successMessage;
            if (paymentStatus.equals("COMPLETED")) {
                successMessage = String.format(
                        "Payment of %.2f processed successfully. Transport fee fully paid!",
                        transportTransactionRequestDTO.getAmount());
            } else {
                successMessage = String.format(
                        "Payment of %.2f processed successfully. Remaining balance: %.2f",
                        transportTransactionRequestDTO.getAmount(),
                        arrearsAfter);
            }

            response.setEntity(responseData);
            response.setMessage(successMessage);
            response.setStatusCode(HttpStatus.CREATED.value());
            auditService.log("TRANSPORT_TRANSACTION", "Created transaction for student ID:", String.valueOf(studentId),
                    "amount:", String.valueOf(transportTransactionRequestDTO.getAmount()), "status:", paymentStatus);

        } catch (RuntimeException e) {
            log.error("Error creating transport transaction", e);
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    /**
     * Resolves the expected transport fee for a vehicle for a specific term/year and direction.
     * Looks up the per-term {@link TransportTermPrice} row and returns the one-way or two-way amount.
     */
    private Double getExpectedFee(Transport transport, TransportType transportType, Term term, Integer year) {
        if (transportType == null || term == null || year == null) {
            return 0.0;
        }

        TransportTermPrice price = transportTermPriceRepository
                .findByVehicleAndTermAndYear(transport, term, year)
                .orElse(null);

        if (price == null) {
            return 0.0;
        }

        Double amount = transportType == TransportType.ONE_WAY
                ? price.getOneWayAmount()
                : price.getTwoWayAmount();

        return amount != null ? amount : 0.0;
    }

    /**
     * Applies a set of per-term price DTOs onto a vehicle, creating {@link TransportTermPrice}
     * child rows. Skips entries missing term or year.
     */
    private void applyTermPrices(Transport transport, java.util.List<TransportRequestDTO.TermPriceDTO> termPrices) {
        if (termPrices == null) {
            return;
        }
        for (TransportRequestDTO.TermPriceDTO tp : termPrices) {
            if (tp == null || tp.getTerm() == null || tp.getYear() == null) {
                continue;
            }
            TransportTermPrice price = TransportTermPrice.builder()
                    .vehicle(transport)
                    .term(tp.getTerm())
                    .year(tp.getYear())
                    .oneWayAmount(tp.getOneWayAmount())
                    .twoWayAmount(tp.getTwoWayAmount())
                    .build();
            transport.getTermPrices().add(price);
        }
    }

    @Override
    public CustomResponse<?> getAllTransportTransactions() {
        CustomResponse<List<TransportTransactionResponseDTO>> response = new CustomResponse<>();

        try {
            log.info("Fetching all transport transactions");

            List<TransportTransactions> transactions = transportTransactionsRepository.findAll();

            if (transactions.isEmpty()) {
                log.info("No transport transactions found in the database");
                response.setEntity(Collections.emptyList());
                response.setMessage("No transport transactions found");
                response.setStatusCode(HttpStatus.OK.value());
                return response;
            }

            log.info("Found {} transport transaction(s)", transactions.size());

            List<TransportTransactionResponseDTO> dtoList = transactions.stream()
                    .map(this::getTransportTransactionResponseDTO)
                    .collect(Collectors.toList());

            response.setEntity(dtoList);
            response.setMessage(String.format("Successfully retrieved %d transport transaction(s)", dtoList.size()));
            response.setStatusCode(HttpStatus.OK.value());

            log.info("Successfully retrieved {} transport transactions", dtoList.size());

        } catch (Exception e) {
            log.error("Error retrieving transport transactions: {}", e.getMessage(), e);
            response.setEntity(null);
            response.setMessage("Failed to retrieve transport transactions: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }

    @NotNull
    private TransportTransactionResponseDTO getTransportTransactionResponseDTO(TransportTransactions transaction) {
        TransportTransactionResponseDTO dto = new TransportTransactionResponseDTO();

        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setPaymentMethod(transaction.getPaymentMethod());
        dto.setTerm(transaction.getTerm());
        dto.setYear(transaction.getYear());
        dto.setTransportType(transaction.getTransportType());
        dto.setTransactionTime(transaction.getTransactionTime());
        dto.setStatus(transaction.getStatus());

        // Student information
        Student student = transaction.getStudent();
        dto.setStudentFullName(student.getFirstName() + " " + student.getLastName());

        // Transport information
        Transport transport = transaction.getTransport();
        dto.setTransportName(transport.getVehicleNumber());

        // Financial information - fetched from persisted fields for accuracy
        dto.setExpectedFee(transaction.getExpectedFee());
        dto.setTotalPaid(transaction.getTotalPaidAfterThis());
        dto.setTotalArrears(transaction.getArrearsAfterThis());

        return dto;
    }

    @Override
    public CustomResponse<?> getUtilizationSummary() {
        CustomResponse<List<TransportUtilization>> response = new CustomResponse<>();
        try {
            List<Transport> allVehicles = transportRepository.findAll();
            List<TransportUtilization> utilizationList = new ArrayList<>();

            for (Transport vehicle : allVehicles) {
                // Get assigned students count for this vehicle
                long assignedStudents = assignTransportRepository.countByVehicle(vehicle);

                // Calculate utilization percentage
                double utilizationPercentage = vehicle.getCapacity() > 0
                        ? (assignedStudents * 100.0) / vehicle.getCapacity()
                        : 0.0;

                // Get all transport transactions for this vehicle
                List<TransportTransactions> transactions = transportTransactionsRepository.findByTransport(vehicle);

                // Calculate expected revenue (sum of each assigned student's per-term fee)
                double expectedRevenue = 0.0;

                List<AssignTransport> assignments = assignTransportRepository.findByVehicle(vehicle);

                for (AssignTransport assignment : assignments) {
                    Double fee = getExpectedFee(
                            vehicle,
                            assignment.getTransportType(),
                            assignment.getTerm(),
                            assignment.getYear());
                    if (fee != null) {
                        expectedRevenue += fee;
                    }
                }

                // Calculate collected revenue (sum of all payments made)
                double collectedRevenue = transactions.stream()
                        .mapToDouble(TransportTransactions::getAmount)
                        .sum();

                // Calculate pending revenue
                double pendingRevenue = expectedRevenue - collectedRevenue;

                TransportUtilization utilization = TransportUtilization.builder()
                        .vehicleId(vehicle.getId().intValue())
                        .vehicleNumber(vehicle.getVehicleNumber())
                        .route(vehicle.getRoute())
                        .capacity(vehicle.getCapacity())
                        .assignedStudents((int) assignedStudents)
                        .utilizationPercentage(Math.round(utilizationPercentage * 100.0) / 100.0)
                        .expectedRevenue(Math.round(expectedRevenue * 100.0) / 100.0)
                        .collectedRevenue(Math.round(collectedRevenue * 100.0) / 100.0)
                        .pendingRevenue(Math.round(pendingRevenue * 100.0) / 100.0)
                        .build();

                utilizationList.add(utilization);
            }

            response.setStatusCode(HttpStatus.OK.value());
            response.setMessage("Utilization summary retrieved successfully");
            response.setEntity(utilizationList);

        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage(e.getMessage());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getStudentTransportArrears(Long studentId) {
        CustomResponse<TransportArrearsSummaryDTO> response = new CustomResponse<>();
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Newest-first so the first transaction seen per group is the latest state.
            List<TransportTransactions> transactions = transportTransactionsRepository
                    .findByStudentIdOrderByTransactionTimeDescIdDesc(studentId);

            List<TransportArrearsLineDTO> lines = buildArrearsLines(transactions);

            double totalExpected = lines.stream().mapToDouble(l -> l.getExpectedFee() != null ? l.getExpectedFee() : 0.0).sum();
            double totalPaid = lines.stream().mapToDouble(l -> l.getTotalPaid() != null ? l.getTotalPaid() : 0.0).sum();
            double totalOutstanding = lines.stream().mapToDouble(l -> l.getOutstanding() != null ? l.getOutstanding() : 0.0).sum();

            TransportArrearsSummaryDTO summary = TransportArrearsSummaryDTO.builder()
                    .studentId(student.getId())
                    .admissionNumber(student.getAdmissionNumber())
                    .fullName(student.getFirstName() + " " + student.getLastName())
                    .lines(lines)
                    .totalExpected(round(totalExpected))
                    .totalPaid(round(totalPaid))
                    .totalOutstanding(round(totalOutstanding))
                    .build();

            response.setEntity(summary);
            response.setMessage("Transport arrears retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.NOT_FOUND.value());
        }
        return response;
    }

    @Override
    public CustomResponse<?> getTransportArrearsForTermYear(Term term, Integer year) {
        CustomResponse<List<TransportArrearsSummaryDTO>> response = new CustomResponse<>();
        try {
            if (term == null || year == null) {
                response.setEntity(null);
                response.setMessage("Both term and year are required");
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return response;
            }

            // Drive the arrears roster from the ASSIGNMENTS for the term/year, not from
            // transactions. Every assigned student is expected to pay their vehicle's per-term
            // fee; a student who has never paid has no transaction row, so a transaction-only scan
            // (the previous behaviour) silently omitted them. Grouping assignments by student lets
            // a student with multiple assignments (e.g. different vehicles) produce one line each.
            List<AssignTransport> assignments = assignTransportRepository.findByTermAndYear(term, year);

            Map<Long, List<AssignTransport>> byStudent = assignments.stream()
                    .filter(a -> a.getStudent() != null && a.getVehicle() != null)
                    .collect(Collectors.groupingBy(a -> a.getStudent().getId(), LinkedHashMap::new, Collectors.toList()));

            List<TransportArrearsSummaryDTO> summaries = new ArrayList<>();
            for (Map.Entry<Long, List<AssignTransport>> entry : byStudent.entrySet()) {
                List<TransportArrearsLineDTO> lines = new ArrayList<>();

                for (AssignTransport a : entry.getValue()) {
                    Transport vehicle = a.getVehicle();
                    TransportType type = a.getTransportType();

                    double expected = orZero(getExpectedFee(vehicle, type, term, year));
                    double paid = orZero(transportTransactionsRepository
                            .sumPaidAmountByStudentAndTransportAndTermAndYear(
                                    entry.getKey(), vehicle.getId(), term, year, type));
                    double outstanding = expected - paid;
                    if (outstanding < 0.0) {
                        outstanding = 0.0;
                    }

                    String status;
                    if (outstanding < 0.01) {
                        status = "COMPLETED";
                    } else if (paid > 0.0) {
                        status = "PARTIAL";
                    } else {
                        status = "UNPAID";
                    }

                    lines.add(TransportArrearsLineDTO.builder()
                            .term(term)
                            .year(year)
                            .transportType(type)
                            .vehicleId(vehicle.getId())
                            .vehicleNumber(vehicle.getVehicleNumber())
                            .route(vehicle.getRoute())
                            .expectedFee(round(expected))
                            .totalPaid(round(paid))
                            .outstanding(round(outstanding))
                            .status(status)
                            .build());
                }

                // Only include students who still owe something for this term.
                double totalOutstanding = lines.stream()
                        .mapToDouble(l -> l.getOutstanding() != null ? l.getOutstanding() : 0.0).sum();
                if (totalOutstanding <= 0.0) {
                    continue;
                }

                double totalExpected = lines.stream().mapToDouble(l -> l.getExpectedFee() != null ? l.getExpectedFee() : 0.0).sum();
                double totalPaid = lines.stream().mapToDouble(l -> l.getTotalPaid() != null ? l.getTotalPaid() : 0.0).sum();

                Student student = entry.getValue().get(0).getStudent();
                summaries.add(TransportArrearsSummaryDTO.builder()
                        .studentId(student.getId())
                        .admissionNumber(student.getAdmissionNumber())
                        .fullName(student.getFirstName() + " " + student.getLastName())
                        .lines(lines)
                        .totalExpected(round(totalExpected))
                        .totalPaid(round(totalPaid))
                        .totalOutstanding(round(totalOutstanding))
                        .build());
            }

            response.setEntity(summaries);
            response.setMessage(String.format("Found %d student(s) with transport arrears for %s %d",
                    summaries.size(), term, year));
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return response;
    }


    private List<TransportArrearsLineDTO> buildArrearsLines(List<TransportTransactions> transactionsNewestFirst) {
        Map<String, TransportArrearsLineDTO> byGroup = new LinkedHashMap<>();

        for (TransportTransactions t : transactionsNewestFirst) {
            Transport vehicle = t.getTransport();
            Long vehicleId = vehicle != null ? vehicle.getId() : null;
            String key = vehicleId + "|" + t.getTerm() + "|" + t.getYear() + "|" + t.getTransportType();

            // First occurrence per group == latest transaction (list is newest-first).
            if (byGroup.containsKey(key)) {
                continue;
            }

            Double expected = t.getExpectedFee();
            Double paid = t.getTotalPaidAfterThis();
            Double outstanding = t.getArrearsAfterThis();

            String status;
            if (outstanding == null || outstanding < 0.01) {
                status = "COMPLETED";
                outstanding = 0.0;
            } else if (paid != null && paid > 0.0) {
                status = "PARTIAL";
            } else {
                status = "UNPAID";
            }

            byGroup.put(key, TransportArrearsLineDTO.builder()
                    .term(t.getTerm())
                    .year(t.getYear())
                    .transportType(t.getTransportType())
                    .vehicleId(vehicleId)
                    .vehicleNumber(vehicle != null ? vehicle.getVehicleNumber() : null)
                    .route(vehicle != null ? vehicle.getRoute() : null)
                    .expectedFee(round(expected))
                    .totalPaid(round(paid))
                    .outstanding(round(outstanding))
                    .status(status)
                    .build());
        }

        return new ArrayList<>(byGroup.values());
    }

    private Double round(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    @NotNull
    private static TransportTransactions getTransportTransactions(
            TransportTransactionRequestDTO transportTransactionRequestDTO,
            Student student,
            Transport transport,
            Double expectedFee,
            Double totalPaidBefore,
            Double totalPaidAfter,
            Double arrearsAfter) {

        TransportTransactions transaction = new TransportTransactions();
        transaction.setAmount(transportTransactionRequestDTO.getAmount());
        transaction.setPaymentMethod(transportTransactionRequestDTO.getPaymentMethod());
        transaction.setTerm(transportTransactionRequestDTO.getTerm());
        transaction.setYear(transportTransactionRequestDTO.getYear());
        transaction.setTransportType(transportTransactionRequestDTO.getTransportType());
        transaction.setStudent(student);
        transaction.setTransport(transport);

        // Set the new fields
        transaction.setExpectedFee(expectedFee);
        transaction.setTotalPaidBeforeThis(totalPaidBefore);
        transaction.setTotalPaidAfterThis(totalPaidAfter);
        transaction.setArrearsAfterThis(arrearsAfter);

        return transaction;
    }

    @NotNull
    private static TransportTransactions getTransportTransactions(
            TransportTransactionRequestDTO transportTransactionRequestDTO, Student student, Transport transport) {
        TransportTransactions transaction = new TransportTransactions();
        transaction.setAmount(transportTransactionRequestDTO.getAmount());
        transaction.setPaymentMethod(transportTransactionRequestDTO.getPaymentMethod());
        transaction.setTerm(transportTransactionRequestDTO.getTerm());
        transaction.setYear(transportTransactionRequestDTO.getYear());
        transaction.setTransportType(transportTransactionRequestDTO.getTransportType());
        transaction.setStudent(student);

        transaction.setTransport(transport);
        return transaction;
    }

    private static String buildStudentName(Student s) {
        String first = s.getFirstName() != null ? s.getFirstName() : "";
        String last = s.getLastName() != null ? s.getLastName() : "";
        return (first + " " + last).trim();
    }

    private TransportResponseDTO mapToResponse(Transport transport) {
        List<TransportResponseDTO.TermPriceDTO> termPrices = transport.getTermPrices() == null
                ? new ArrayList<>()
                : transport.getTermPrices().stream()
                        .map(tp -> TransportResponseDTO.TermPriceDTO.builder()
                                .id(tp.getId())
                                .term(tp.getTerm())
                                .year(tp.getYear())
                                .oneWayAmount(tp.getOneWayAmount())
                                .twoWayAmount(tp.getTwoWayAmount())
                                .build())
                        .collect(Collectors.toList());

        return TransportResponseDTO.builder()
                .id(transport.getId())
                .vehicleNumber(transport.getVehicleNumber())
                .vehicleType(transport.getVehicleType())
                .capacity(transport.getCapacity())
                .driverName(transport.getDriverName())
                .driverContact(transport.getDriverContact())
                .route(transport.getRoute())
                .status(transport.getStatus())
                .termPrices(termPrices)
                .build();
    }

}
