package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Transport.Boarding.Requests.StudentTransportBiometricEnrollRequest;
import com.EduePoa.EP.Transport.Boarding.Requests.TransportBoardingScanRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Transport boarding attendance API.
 *
 * <pre>
 * POST /api/v1/transport/boarding/scan               transport_boarding:mark
 * GET  /api/v1/transport/boarding                     transport_boarding:read
 * GET  /api/v1/transport/boarding/student/{studentId} transport_boarding:read
 * POST /api/v1/transport/biometric/enroll             transport_biometric:enroll
 * </pre>
 *
 * All responses use {@link com.EduePoa.EP.Utils.CustomResponse} and follow the existing transport
 * controller convention {@code ResponseEntity.status(response.getStatusCode()).body(response)}.
 */
@RestController
@RequiredArgsConstructor
public class TransportBoardingController {

    private final TransportBoardingService transportBoardingService;

    @PostMapping("api/v1/transport/boarding/scan")
    @PreAuthorize("hasPermission(null, 'transport_boarding:mark')")
    ResponseEntity<?> scan(@Valid @RequestBody TransportBoardingScanRequest request) {
        var response = transportBoardingService.recordBatch(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("api/v1/transport/boarding")
    @PreAuthorize("hasPermission(null, 'transport_boarding:read')")
    ResponseEntity<?> manifest(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BoardingLeg leg) {
        var response = transportBoardingService.getVehicleManifest(vehicleId, date, leg);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("api/v1/transport/boarding/students")
    @PreAuthorize("hasPermission(null, 'transport_boarding:read')")
    ResponseEntity<?> transportStudents(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var response = transportBoardingService.getTransportStudents(vehicleId, date);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("api/v1/transport/boarding/student/{studentId}")
    @PreAuthorize("hasPermission(null, 'transport_boarding:read')")
    ResponseEntity<?> studentHistory(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var response = transportBoardingService.getStudentHistory(studentId, from, to);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("api/v1/transport/biometric/enroll")
    @PreAuthorize("hasPermission(null, 'transport_biometric:enroll')")
    ResponseEntity<?> enroll(@Valid @RequestBody StudentTransportBiometricEnrollRequest request) {
        var response = transportBoardingService.enrollIdentifier(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
