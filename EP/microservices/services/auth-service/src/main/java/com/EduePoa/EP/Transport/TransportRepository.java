package com.EduePoa.EP.Transport;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportRepository extends JpaRepository<Transport, Long> {
    Long countByStatus(String status);

    boolean existsByVehicleNumber(String vehicleNumber);
}
