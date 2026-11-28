package com.fincore.checkout.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice(assignableTypes = {AdminController.class, AiAdminController.class})
public class AdminExceptionHandler {
    @ExceptionHandler(HttpClientErrorException.class)
    public ProblemDetail clientError(HttpClientErrorException error) {
        int status = error.getStatusCode().value();
        return switch (status) {
            case 400 -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid admin request");
            case 404 -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Requested record not found");
            case 409 -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "Request conflicts with current state; refresh the record before retrying");
            default -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Admin data is unavailable");
        };
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail unavailable(RestClientException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Admin data is temporarily unavailable");
    }
}
