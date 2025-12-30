package com.EduePoa.EP.MPesa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class MpesaPaymentRequestDTO {

    @JsonProperty(value = "phoneNumber")
    private String phoneNumber;
    @JsonProperty(value = "amount")
    private Double amounts;
    @JsonProperty
    private Long accountReference;


//    @JsonProperty
//    private String transactionDescription;

}
