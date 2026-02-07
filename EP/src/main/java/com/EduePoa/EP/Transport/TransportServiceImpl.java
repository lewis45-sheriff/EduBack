package com.EduePoa.EP.Transport;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.AssignTransportResponseDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.StudentTransportDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.Responses.TransportResponseDTO;
import com.EduePoa.EP.Transport.Responses.TransportUtilization;
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
    private final AuditService auditService;

    @Override
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
                    .routePriceOneWay(transportRequestDTO.getRoutePriceOneWay())
                    .routePriceTwoWay(transportRequestDTO.getRoutePriceTwoWay())
                    .status(transportRequestDTO.getStatus())
                    .build();

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
            transport.setRoutePriceOneWay(transportRequestDTO.getRoutePriceOneWay());
            transport.setRoutePriceTwoWay(transportRequestDTO.getRoutePriceTwoWay());
            transport.setStatus(transportRequestDTO.getStatus());

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
    public CustomResponse<?> assign(AssignTransportRequestDTO request) {
        CustomResponse<AssignTransport> response = new CustomResponse<>();

        try {
            // Validate Student
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Prevent duplicate assignment
            if (assignTransportRepository.findByStudent(student).isPresent()) {
                throw new RuntimeException("Student already has an assigned vehicle");
            }

            // Validate Vehicle
            Transport vehicle = transportRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));

            // Build Entity
            AssignTransport assignTransport = AssignTransport.builder()
                    .student(student)
                    .vehicle(vehicle)
                    .pickupLocation(request.getPickupLocation())
                    .transportType(request.getTransportType())
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

            // Get the expected fee based on transport type
            Double expectedFee = getExpectedFee(transport, transportTransactionRequestDTO.getTransportType());

            if (expectedFee == null || expectedFee == 0.0) {
                response.setEntity(null);
                response.setMessage(
                        "Transport fee not configured for " + transportTransactionRequestDTO.getTransportType());
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

    private Double getExpectedFee(Transport transport, String transportType) {
        if (transportType == null) {
            return 0.0;
        }

        String normalized = transportType.trim().toUpperCase();

        if (normalized.contains("ONE") || normalized.equals("ONE_WAY")) {
            return transport.getRoutePriceOneWay();
        } else if (normalized.contains("TWO") || normalized.equals("TWO_WAY")) {
            return transport.getRoutePriceTwoWay();
        }

        return 0.0;
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

                // Calculate expected revenue (sum of all assigned students' fees)
                double expectedRevenue = 0.0;

                List<AssignTransport> assignments = assignTransportRepository.findByVehicle(vehicle);

                for (AssignTransport assignment : assignments) {
                    String transportType = assignment.getTransportType();
                    System.out.println("Assignment ID: " + assignment.getId() +
                            ", Transport Type: '" + transportType + "'");

                    if (transportType == null) {
                        System.out.println("  -> Transport type is NULL, skipping");
                        continue;
                    }

                    String normalizedType = transportType.trim().toUpperCase();

                    if (normalizedType.contains("ONE") || normalizedType.equals("ONE_WAY")
                            || normalizedType.equals("ONEWAY")) {
                        double price = vehicle.getRoutePriceOneWay() != null ? vehicle.getRoutePriceOneWay() : 0.0;
                        System.out.println("  -> Matched ONE_WAY, Price: " + price);
                        expectedRevenue += price;
                    } else if (normalizedType.contains("TWO") || normalizedType.equals("TWO_WAY")
                            || normalizedType.equals("TWOWAY")) {
                        double price = vehicle.getRoutePriceTwoWay() != null ? vehicle.getRoutePriceTwoWay() : 0.0;
                        System.out.println("  -> Matched TWO_WAY, Price: " + price);
                        expectedRevenue += price;
                    } else {
                        System.out.println("  -> NO MATCH for transport type: '" + transportType + "'");
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

    private TransportResponseDTO mapToResponse(Transport transport) {
        return TransportResponseDTO.builder()
                .id(transport.getId())
                .vehicleNumber(transport.getVehicleNumber())
                .vehicleType(transport.getVehicleType())
                .capacity(transport.getCapacity())
                .driverName(transport.getDriverName())
                .driverContact(transport.getDriverContact())
                .route(transport.getRoute())
                .routePriceOneWay(transport.getRoutePriceOneWay())
                .routePriceTwoWay(transport.getRoutePriceTwoWay())
                .status(transport.getStatus())
                .build();
    }

}
