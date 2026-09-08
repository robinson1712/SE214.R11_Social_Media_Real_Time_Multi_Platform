package com.socialapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String fullName,
        String phone,
        /** "MALE" / "FEMALE" / "OTHER", optional — forwarded to user-service as-is. */
        String gender,
        @Past LocalDate dob
) {
}
