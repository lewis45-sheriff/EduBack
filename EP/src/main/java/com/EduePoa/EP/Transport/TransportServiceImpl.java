package com.EduePoa.EP.Transport;

import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.AssignTransportResponseDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.StudentTransportDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.Responses.TransportResponseDTO;
import com.EduePoa.EP.Transport.TransportTransactions.Requests.TransportTransactionRequestDTO;
import com.EduePoa.EP.Transport.TransportTransactions.Responses.TransportTransactionResponseDTO;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactions;
import com.EduePoa.EP.Transport.TransportTransactions.TransportTransactionsRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransportServiceImpl implements  TransportService {
    private final TransportRepository transportRepository;
    private final StudentRepository studentRepository;
    private final AssignTransportRepository assignTransportRepository;
    private final TransportTransactionsRepository transportTransactionsRepository;

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
            //  Validate Student
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            //  Prevent duplicate assignment
            if (assignTransportRepository.findByStudent(student).isPresent()) {
                throw new RuntimeException("Student already has an assigned vehicle");
            }

            //  Validate Vehicle
            Transport vehicle = transportRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));

            //  Build Entity
            AssignTransport assignTransport = AssignTransport.builder()
                    .student(student)
                    .vehicle(vehicle)
                    .pickupLocation(request.getPickupLocation())
                    .transportType(request.getTransportType())
                    .build();

            //  Save
            AssignTransport saved = assignTransportRepository.save(assignTransport);

            // Success Response
            response.setEntity(saved);
            response.setMessage("Transport assigned to student successfully");
            response.setStatusCode(HttpStatus.CREATED.value());

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
                                            .concat(at.getStudent().getLastName())
                            )
                            .vehicleId(at.getVehicle().getId())
                            .vehiclePlateNumber(at.getVehicle().getVehicleNumber())
                            .pickupLocation(at.getPickupLocation())
                            .transportType(at.getTransportType())
                            .build()
                    ).toList();

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

            List<StudentTransportDTO> data = assignments.stream().map(at ->
                    StudentTransportDTO.builder()
                            .studentId(at.getStudent().getId())
                            .admissionNumber(at.getStudent().getAdmissionNumber())
                            .fullName(
                                    at.getStudent().getFirstName() + " " +
                                            at.getStudent().getLastName()
                            )
                            .pickupLocation(at.getPickupLocation())
                            .transportType(at.getTransportType())
                            .vehicleName(at.getVehicle().getVehicleNumber())
                            .build()
            ).toList();

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
    public CustomResponse<?> createTransportTransaction(Long studentId,TransportTransactionRequestDTO transportTransactionRequestDTO) {

        CustomResponse<TransportTransactions> response = new CustomResponse<>();

        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

           
            Transport transport = transportRepository.findById(transportTransactionRequestDTO.getTransportId())
                    .orElseThrow(() -> new RuntimeException("Transport not found"));


            TransportTransactions transaction = getTransportTransactions(transportTransactionRequestDTO, student, transport);


            TransportTransactions savedTransaction = transportTransactionsRepository.save(transaction);

            response.setEntity(savedTransaction);
            response.setMessage("Transport transaction created successfully");
            response.setStatusCode(HttpStatus.CREATED.value());

        } catch (RuntimeException e) {
            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }
    @Override
    public CustomResponse<?> getAllTransportTransactions() {

        CustomResponse<List<TransportTransactionResponseDTO>> response = new CustomResponse<>();

        try {

            List<TransportTransactions> transactions =
                    transportTransactionsRepository.findAll();

            if (transactions.isEmpty()) {
                response.setEntity(Collections.emptyList());
                response.setMessage("No transport transactions found");
                response.setStatusCode(HttpStatus.OK.value());
                return response;
            }

            List<TransportTransactionResponseDTO> dtoList = new ArrayList<>();

            for (TransportTransactions transaction : transactions) {

                TransportTransactionResponseDTO dto =
                        new TransportTransactionResponseDTO();

                dto.setId(transaction.getId());
                dto.setAmount(transaction.getAmount());
                dto.setPaymentMethod(transaction.getPaymentMethod());
                dto.setTerm(transaction.getTerm());
                dto.setYear(transaction.getYear());
                dto.setTransportType(transaction.getTransportType());


                Student student = transaction.getStudent();
                dto.setStudentFullName(
                        student.getFirstName() + " " + student.getLastName()
                );


                Transport transport = transaction.getTransport();
                dto.setTransportName(transport.getVehicleNumber());
                // change to getVehicleName() if that's what you use

                dtoList.add(dto);
            }

            response.setEntity(dtoList);
            response.setMessage("Transport transactions retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());

        } catch (RuntimeException e) {

            response.setEntity(null);
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return response;
    }


    @NotNull
    private static TransportTransactions getTransportTransactions(TransportTransactionRequestDTO transportTransactionRequestDTO, Student student, Transport transport) {
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
