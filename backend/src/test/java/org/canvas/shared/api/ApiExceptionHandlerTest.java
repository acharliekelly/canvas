package org.canvas.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsServletUploadLimitFailuresToTheUploadProblemContract() {
        var problem = handler.uploadTooLarge(new MaxUploadSizeExceededException(1024));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid artwork upload");
        assertThat(problem.getProperties())
                .containsEntry("code", "file_too_large")
                .containsEntry("field", "image");
    }
}
