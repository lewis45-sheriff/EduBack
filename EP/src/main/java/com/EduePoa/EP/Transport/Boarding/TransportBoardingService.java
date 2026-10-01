package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Transport.AssignTransport.AssignTransport;
import com.EduePoa.EP.Transport.Boarding.Requests.StudentTransportBiometricEnrollRequest;
import com.EduePoa.EP.Transport.Boarding.Requests.TransportBoardingScanRequest;
import com.EduePoa.EP.Utils.CustomResponse;

import java.time.LocalDate;
import java.util.Set;

/**
 * Transport boarding attendance operations. All methods return {@link CustomResponse} following the
 * existing transport service conventions.
 */
public interface TransportBoardingService {

    CustomResponse<?> recordBatch(TransportBoardingScanRequest request);
    CustomResponse<?> getVehicleManifest(Long vehicleId, LocalDate date, BoardingLeg leg);
    CustomResponse<?> getStudentHistory(Long studentId, LocalDate from, LocalDate to);
    CustomResponse<?> enrollIdentifier(StudentTransportBiometricEnrollRequest request);

    CustomResponse<?> getTransportStudents(Long vehicleId, LocalDate date);

    Set<BoardingLeg> eligibleLegs(AssignTransport assignment);
}
