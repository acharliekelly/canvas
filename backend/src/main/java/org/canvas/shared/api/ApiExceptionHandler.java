package org.canvas.shared.api;

import org.canvas.artwork.ArtworkService.ArtworkProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge(MaxUploadSizeExceededException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Image exceeds the configured upload size limit.");
        problem.setTitle("Invalid artwork upload");
        problem.setProperty("code", "image_too_large");
        return problem;
    }

    @ExceptionHandler(ArtworkProblem.class)
    ProblemDetail artworkProblem(ArtworkProblem error) {
        HttpStatus status = switch (error.getCode()) {
            case "artwork_not_found" -> HttpStatus.NOT_FOUND;
            case "storage_unavailable", "persistence_unavailable" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        problem.setTitle(status == HttpStatus.BAD_REQUEST ? "Invalid artwork upload" : status.getReasonPhrase());
        problem.setProperty("code", error.getCode());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                error.getParameterName() + " is required.");
        problem.setTitle("Invalid artwork upload");
        problem.setProperty("code", "invalid_request");
        return problem;
    }
}
