package com.whatwewillwatchtonight.controller.error;

import com.whatwewillwatchtonight.controller.dto.ErrorResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the API's exceptions into {@code 400} responses carrying an
 * {@link ErrorResponseDto} so every failure has the same shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponseDto> handleApiException(ApiException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("Missing required parameter: " + exception.getParameterName() + "."));
    }
}
