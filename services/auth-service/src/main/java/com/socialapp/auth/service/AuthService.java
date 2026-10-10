package com.socialapp.auth.service;

import com.socialapp.auth.config.AdminBootstrapToken;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdminBootstrapToken adminBootstrapToken;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return createAccount(request, List.of("USER"));
    }

    /**
     * Creates the first administrator through a one-time operator-only path.
     * SERIALIZABLE protects the empty-admin check when multiple instances share a database.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuthResponse bootstrapAdmin(RegisterRequest request, String suppliedToken) {
        if (!adminBootstrapToken.matches(suppliedToken)) {
            throw new UnauthorizedException("Invalid admin bootstrap token");
        }
        if (accountRepository.countByRole("ADMIN") > 0) {
            throw new ConflictException("Admin bootstrap has already been completed");
        }
        AuthResponse response = createAccount(request, List.of("USER", "ADMIN"));
        afterCommit(() -> log.info("Initial admin bootstrap completed for accountId={}", response.accountId()));
        return response;
    }

    private AuthResponse createAccount(RegisterRequest request, List<String> roles) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        Account account = Account.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .fullName(request.fullName())
                .status(AccountStatus.ACTIVE)
                .roles(new ArrayList<>(roles))
                .build();
        account = accountRepository.save(account);

        UserRegisteredEvent event = new UserRegisteredEvent(account.getId(), account.getEmail(), account.getFullName(),
                request.gender(), request.dob(), Instant.now());
        afterCommit(() -> kafkaTemplate.send(KafkaTopics.USER_REGISTERED, event));

        return issueTokens(account);
    }

    // A failed SERIALIZABLE commit must not publish a user that never existed.
    // This does not replace a durable outbox: delivery after a process crash still needs one.
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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

        if (account.getStatus() == AccountStatus.BANNED) {
            throw new UnauthorizedException("Invalid credentials");
        }
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
