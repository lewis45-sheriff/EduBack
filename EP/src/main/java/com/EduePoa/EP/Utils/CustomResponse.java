package com.EduePoa.EP.Utils;

import lombok.*;
import org.springframework.http.HttpStatus;

import static org.hibernate.query.results.Builders.entity;

@Data
public class CustomResponse<T> {

    private String message;
    private Integer statusCode;
    private T entity;

    public CustomResponse() {
        this.statusCode = HttpStatus.OK.value();
    }

    public CustomResponse(String message, Integer statusCode, T entity) {
        this.message = message;
        this.statusCode = statusCode;
        this.entity = entity;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public T getEntity() {
        return entity;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setEntity(T entity) {
        this.entity = entity;
    }
}
