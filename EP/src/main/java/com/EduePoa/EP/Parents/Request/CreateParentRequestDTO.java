package com.EduePoa.EP.Parents.Request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class CreateParentRequestDTO {

    @JsonAlias("parent")
    private ParentInfoDTO parent;

    private String firstName;
    private String lastName;
    private String otherNames;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private String occupation;
    private String address;
    private Boolean portalAccessEnabled;
    private Boolean receiveSms;
    private Boolean receiveEmail;

    public ParentInfoDTO resolve() {
        if (parent != null) {
            return parent;
        }
        ParentInfoDTO dto = new ParentInfoDTO();
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setOtherNames(otherNames);
        dto.setPhoneNumber(phoneNumber);
        dto.setAlternatePhoneNumber(alternatePhoneNumber);
        dto.setEmail(email);

        dto.setNationalIdOrPassport(nationalIdOrPassport);
        dto.setOccupation(occupation);
        dto.setAddress(address);
        dto.setPortalAccessEnabled(portalAccessEnabled != null && portalAccessEnabled);
        dto.setReceiveSms(receiveSms == null || receiveSms);
        dto.setReceiveEmail(receiveEmail != null && receiveEmail);
        return dto;
    }
}
