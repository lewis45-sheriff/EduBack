package com.EduePoa.EP.Transport.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportResponseDTO {
    private Long id;
    private String vehicleNumber;

    private String vehicleType;

    private Integer capacity;

    private String driverName;

    private String driverContact;

    private String route;

    private String status;

    /**
     * Per-term prices for this vehicle.
     */
    @Builder.Default
    private List<TermPriceDTO> termPrices = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TermPriceDTO {
        private Long id;
        private Term term;
        private Integer year;
        private Double oneWayAmount;
        private Double twoWayAmount;
    }
}
