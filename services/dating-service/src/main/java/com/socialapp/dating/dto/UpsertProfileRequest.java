package com.socialapp.dating.dto;

import com.socialapp.dating.entity.Gender;
import com.socialapp.dating.entity.GenderPreference;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.List;

public record UpsertProfileRequest(
        @NotNull Gender gender,
        @NotNull @Past LocalDate birthDate,
        String bio,
        List<String> interests,
        int minAgePreference,
        int maxAgePreference,
        GenderPreference genderPreference,
        List<String> photos
) {
}
