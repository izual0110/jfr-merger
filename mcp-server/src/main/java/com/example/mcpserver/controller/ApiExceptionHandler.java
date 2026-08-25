package com.example.mcpserver.controller;

import com.example.mcpserver.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleIllegalArgument(IllegalArgumentException exception) {
        return new ErrorResponseDto(OffsetDateTime.now(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleValidation(Exception exception) {
        return new ErrorResponseDto(OffsetDateTime.now(), exception.getMessage());
    }
}
