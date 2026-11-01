package com.socialapp.auth.controller;

import com.socialapp.auth.dto.AuthResponse;
import com.socialapp.auth.dto.RegisterRequest;
import com.socialapp.auth.service.AuthService;
import com.socialapp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-only bootstrap endpoint; auth-service must stay on loopback. */
@Hidden
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<ApiResponse<AuthResponse>> bootstrapAdmin(
            @RequestHeader(value = "X-Admin-Bootstrap-Token", required = false) String token,
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin bootstrapped", authService.bootstrapAdmin(request, token)));
    }
}
