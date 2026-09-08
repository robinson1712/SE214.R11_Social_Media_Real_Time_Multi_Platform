package com.socialapp.auth.dto;

import com.socialapp.auth.entity.AccountStatus;

import java.util.List;

public record AccountResponse(
        String id,
        String email,
        List<String> roles,
        AccountStatus status
) {
}
