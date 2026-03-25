package com.EduePoa.EP.Communications.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class SmsSendRequest {

    @NotBlank(message = "Message content must not be blank")
    @Size(max = 1600, message = "Message content must not exceed 1600 characters (10 SMS segments)")
    private String content;
}
