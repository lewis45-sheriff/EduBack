package com.EduePoa.EP.Utils;

import lombok.*;
import org.springframework.http.HttpStatus;

import static org.hibernate.query.results.Builders.entity;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomResponse<T> {

    private String message;
    private Integer statusCode;
    private T entity;


}
