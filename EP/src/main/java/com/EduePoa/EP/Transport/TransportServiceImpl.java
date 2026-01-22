package com.EduePoa.EP.Transport;

import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.AssignTransport.AssignTransportRepository;
import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.AssignTransport.Response.AssignTransportResponseDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.Responses.TransportResponseDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransportServiceImpl implements  TransportService {
    private final TransportRepository transportRepository;
    private final StudentRepository studentRepository;
    private final AssignTransportRepository assignTransportRepository;

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
