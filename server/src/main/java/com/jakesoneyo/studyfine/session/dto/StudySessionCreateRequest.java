package com.jakesoneyo.studyfine.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record StudySessionCreateRequest(
    @NotNull LocalDate sessionDate,
    @NotBlank @Size(min = 1, max = 100) String title
) {
}
