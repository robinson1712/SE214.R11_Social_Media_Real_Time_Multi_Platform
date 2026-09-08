package com.socialapp.user.dto;

import com.socialapp.user.entity.Gender;

import java.time.LocalDate;

public record UpdateProfileRequest(
        String fullName,
        String bio,
        LocalDate dob,
        Gender gender,
        String location,
        String workplace,
        Boolean readReceiptsEnabled
) {
}
