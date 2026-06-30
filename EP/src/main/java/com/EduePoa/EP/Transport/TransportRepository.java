package com.EduePoa.EP.Transport;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

public interface TransportRepository extends TenantAwareRepository<Transport, Long> {
    Long countByStatus(String status);

    boolean existsByVehicleNumber(String vehicleNumber);
}
