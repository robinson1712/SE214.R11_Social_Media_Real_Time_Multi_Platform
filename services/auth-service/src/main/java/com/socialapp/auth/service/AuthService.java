package com.socialapp.auth.service;

import com.socialapp.auth.config.AdminEmailAllowlist;
import com.socialapp.auth.dto.AccessTokenResponse;
import com.socialapp.auth.dto.AccountResponse;
import com.socialapp.auth.dto.AuthResponse;
import com.socialapp.auth.dto.LoginRequest;
import com.socialapp.auth.dto.RefreshRequest;
import com.socialapp.auth.dto.RegisterRequest;
import com.socialapp.auth.entity.Account;
import com.socialapp.auth.entity.AccountStatus;
import com.socialapp.auth.entity.RefreshToken;
import com.socialapp.auth.repository.AccountRepository;
import com.socialapp.auth.repository.RefreshTokenRepository;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.UserRegisteredEvent;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdminEmailAllowlist adminEmailAllowlist;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        List<String> roles = adminEmailAllowlist.isAdmin(request.email())
                ? List.of("USER", "ADMIN")
                : List.of("USER");

        Account account = Account.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .fullName(request.fullName())
                .status(AccountStatus.ACTIVE)
                .roles(new ArrayList<>(roles))
                .build();
        account = accountRepository.save(account);

        kafkaTemplate.send(KafkaTopics.USER_REGISTERED,
                new UserRegisteredEvent(account.getId(), account.getEmail(), account.getFullName(),
                        request.gender(), request.dob(), Instant.now()));

        return issueTokens(account);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        if (account.getStatus() == AccountStatus.BANNED) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return issueTokens(account);
    }

    @Transactional
    public AccessTokenResponse refresh(RefreshRequest request) {
        RefreshToken tokenRow = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (tokenRow.isRevoked() || tokenRow.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        Account account = accountRepository.findById(tokenRow.getAccountId())
                .orElseThrow(() -> new UnauthorizedException("Account not found"));

        String accessToken = jwtTokenProvider.generateAccessToken(account.getId(), account.getRoles());
        return new AccessTokenResponse(accessToken);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        RefreshToken tokenRow = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        tokenRow.setRevoked(true);
        refreshTokenRepository.save(tokenRow);
    }

    public AccountResponse me(String accountId) {
        if (accountId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return new AccountResponse(account.getId(), account.getEmail(), account.getRoles(), account.getStatus());
    }

    private AuthResponse issueTokens(Account account) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.getId(), account.getRoles());
        String refreshToken = jwtTokenProvider.generateRefreshToken(account.getId());

        RefreshToken tokenRow = RefreshToken.builder()
                .accountId(account.getId())
                .token(refreshToken)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenRow);

        return new AuthResponse(account.getId(), account.getEmail(), accessToken, refreshToken);
    }
}
