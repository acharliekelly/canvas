package me.acharliekelly.canvas.shared.api;

import me.acharliekelly.canvas.artwork.ArtworkService.ArtworkProblem;
import me.acharliekelly.canvas.caption.CaptionJobService.CaptionProblem;
import me.acharliekelly.canvas.description.DescriptionService.DescriptionProblem;
import me.acharliekelly.canvas.publication.PublicationService.PublicationProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PublicationProblem.class)
    ProblemDetail publicationProblem(PublicationProblem error) {
        HttpStatus status = switch (error.getCode()) {
            case "artwork_not_found", "public_artwork_not_found", "public_asset_not_found" -> HttpStatus.NOT_FOUND;
            case "stale_version" -> HttpStatus.CONFLICT;
            case "public_image_unavailable", "public_asset_unavailable", "asset_generation_unavailable" ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", error.getCode());
        return problem;
    }

    @ExceptionHandler(CaptionProblem.class)
    ProblemDetail captionProblem(CaptionProblem error) {
        HttpStatus status = switch (error.getCode()) {
            case "artwork_not_found", "caption_job_not_found" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", error.getCode());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadTooLarge(MaxUploadSizeExceededException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Image exceeds the configured upload size limit.");
        problem.setTitle("Invalid artwork upload");
        problem.setProperty("code", "file_too_large");
        problem.setProperty("field", "image");
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
        if (error.getField() != null) {
            problem.setProperty("field", error.getField());
        }
        return problem;
    }

    @ExceptionHandler(DescriptionProblem.class)
    ProblemDetail descriptionProblem(DescriptionProblem error) {
        HttpStatus status = switch (error.getCode()) {
            case "artwork_not_found", "description_not_found" -> HttpStatus.NOT_FOUND;
            case "stale_version" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        problem.setTitle(status == HttpStatus.BAD_REQUEST ? "Invalid description" : status.getReasonPhrase());
        problem.setProperty("code", error.getCode());
        if (error.getField() != null) {
            problem.setProperty("field", error.getField());
        }
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticConflict(OptimisticLockingFailureException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This resource changed after it was loaded. Refresh and try again.");
        problem.setTitle("Conflict");
        problem.setProperty("code", "stale_version");
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                error.getParameterName() + " is required.");
        problem.setTitle("Invalid artwork upload");
        problem.setProperty("code", "invalid_request");
        problem.setProperty("field", error.getParameterName());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missingPart(MissingServletRequestPartException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                error.getRequestPartName() + " is required.");
        problem.setTitle("Invalid artwork upload");
        problem.setProperty("code", "invalid_request");
        problem.setProperty("field", error.getRequestPartName());
        return problem;
    }
}
