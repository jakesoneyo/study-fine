package com.jakesoneyo.studyfine.security.dto;

import com.jakesoneyo.studyfine.security.LoginEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @LoginEmail @Size(max = 255) String email,
    @NotBlank @Size(max = 100) String password
) {
}
