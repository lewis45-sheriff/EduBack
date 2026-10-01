package com.EduePoa.EP.Transport.Request;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportRequestDTO {

    private String vehicleNumber;

    private String vehicleType;

    private Integer capacity;

    private String driverName;

    private String driverContact;

    private String route;

    private String status;

    /**
     * Per-term prices for this vehicle. Each entry defines the one-way and two-way
     * amounts for a specific term and year.
     */
    @Builder.Default
    private List<TermPriceDTO> termPrices = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TermPriceDTO {
        private Term term;
        private Integer year;
        private Double oneWayAmount;
        private Double twoWayAmount;
    }
}
