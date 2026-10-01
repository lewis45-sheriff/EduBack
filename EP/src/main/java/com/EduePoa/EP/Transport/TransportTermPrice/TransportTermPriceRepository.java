package com.EduePoa.EP.Transport.TransportTermPrice;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.Transport.Transport;

import java.util.List;
import java.util.Optional;

public interface TransportTermPriceRepository extends TenantAwareRepository<TransportTermPrice, Long> {

    List<TransportTermPrice> findByVehicle(Transport vehicle);

    Optional<TransportTermPrice> findByVehicleAndTermAndYear(Transport vehicle, Term term, Integer year);

    Optional<TransportTermPrice> findByVehicleIdAndTermAndYear(Long vehicleId, Term term, Integer year);

    void deleteByVehicle(Transport vehicle);
}
