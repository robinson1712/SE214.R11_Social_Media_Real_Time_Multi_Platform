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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthService — repositories, password encoding, JWT issuance,
 * and Kafka publishing are all mocked, so these exercise only the service's own
 * decisions (duplicate-email rejection, credential checks, token lifecycle).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private AdminEmailAllowlist adminEmailAllowlist;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // KafkaTemplate has no no-arg constructor Mockito can proxy cleanly via
        // @Mock in every environment, so build it explicitly and hand it to the
        // service — @InjectMocks still wires the @Mock fields above by type.
        kafkaTemplate = mock(KafkaTemplate.class);
        // Real instance (not a mock) — it's a plain value object once constructed,
        // cheaper and clearer to just build it with the allowlist a given test needs.
        adminEmailAllowlist = new AdminEmailAllowlist("admin@social.app, root@social.app");
        authService = new AuthService(accountRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider, kafkaTemplate, adminEmailAllowlist);
    }

    private Account activeAccount(String id, String email, String hash) {
        return Account.builder()
                .id(id)
                .email(email)
                .passwordHash(hash)
                .fullName("Test User")
                .status(AccountStatus.ACTIVE)
                .roles(List.of("USER"))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void register_createsAccountIssuesTokensAndPublishesEvent() {
        RegisterRequest request = new RegisterRequest("new@social.app", "P@ssw0rd", "New User", null, null, null);
        when(accountRepository.existsByEmail("new@social.app")).thenReturn(false);
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("hashed");
        // Account.id is only assigned by @PrePersist on a real save — simulate that here.
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("acc-generated-1");
            }
            return saved;
        });
        when(jwtTokenProvider.generateAccessToken(eq("acc-generated-1"), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("acc-generated-1")).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        AuthResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("new@social.app");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(refreshTokenRepository).save(any(RefreshToken.class));

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.USER_REGISTERED), eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo("new@social.app");
        assertThat(eventCaptor.getValue().fullName()).isEqualTo("New User");
    }

    @Test
    void register_emailOnAdminAllowlist_grantsAdminRoleInAdditionToUser() {
        RegisterRequest request = new RegisterRequest("admin@social.app", "P@ssw0rd", "Site Admin", null, null, null);
        when(accountRepository.existsByEmail("admin@social.app")).thenReturn(false);
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        authService.register(request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void register_emailNotOnAdminAllowlist_getsUserRoleOnly() {
        RegisterRequest request = new RegisterRequest("nobody-special@social.app", "P@ssw0rd", "Regular User", null, null, null);
        when(accountRepository.existsByEmail("nobody-special@social.app")).thenReturn(false);
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        authService.register(request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly("USER");
    }

    @Test
    void register_duplicateEmail_throwsConflictAndNeverSaves() {
        RegisterRequest request = new RegisterRequest("taken@social.app", "P@ssw0rd", "Someone", null, null, null);
        when(accountRepository.existsByEmail("taken@social.app")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);

        verify(accountRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void login_correctCredentials_issuesTokens() {
        Account account = activeAccount("acc-1", "alice@social.app", "hashed");
        LoginRequest request = new LoginRequest("alice@social.app", "P@ssw0rd");
        when(accountRepository.findByEmail("alice@social.app")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("P@ssw0rd", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(eq("acc-1"), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("acc-1")).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accountId()).isEqualTo("acc-1");
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        Account account = activeAccount("acc-1", "alice@social.app", "hashed");
        when(accountRepository.findByEmail("alice@social.app")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@social.app", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(accountRepository.findByEmail("ghost@social.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@social.app", "whatever")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_bannedAccount_throwsUnauthorizedEvenWithCorrectPassword() {
        Account account = activeAccount("acc-1", "banned@social.app", "hashed");
        account.setStatus(AccountStatus.BANNED);
        when(accountRepository.findByEmail("banned@social.app")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("P@ssw0rd", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("banned@social.app", "P@ssw0rd")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_validToken_issuesNewAccessToken() {
        RefreshToken tokenRow = RefreshToken.builder()
                .id("rt-1").accountId("acc-1").token("refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
        Account account = activeAccount("acc-1", "alice@social.app", "hashed");
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(tokenRow));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(jwtTokenProvider.generateAccessToken(eq("acc-1"), anyList())).thenReturn("new-access-token");

        AccessTokenResponse response = authService.refresh(new RefreshRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken tokenRow = RefreshToken.builder()
                .id("rt-1").accountId("acc-1").token("expired-token")
                .expiresAt(Instant.now().minusSeconds(1)).revoked(false).build();
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(tokenRow));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired-token")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        RefreshToken tokenRow = RefreshToken.builder()
                .id("rt-1").accountId("acc-1").token("revoked-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(true).build();
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(tokenRow));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("revoked-token")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("nope")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_validToken_marksItRevoked() {
        RefreshToken tokenRow = RefreshToken.builder()
                .id("rt-1").accountId("acc-1").token("refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(tokenRow));

        authService.logout(new RefreshRequest("refresh-token"));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    void me_authenticatedKnownAccount_returnsAccountResponse() {
        Account account = activeAccount("acc-1", "alice@social.app", "hashed");
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        AccountResponse response = authService.me("acc-1");

        assertThat(response.email()).isEqualTo("alice@social.app");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void me_noCurrentUser_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.me(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void me_accountIdNotFound_throwsResourceNotFound() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
