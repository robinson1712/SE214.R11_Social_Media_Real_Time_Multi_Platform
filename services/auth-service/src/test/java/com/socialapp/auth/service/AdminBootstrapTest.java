package com.socialapp.auth.service;

import com.socialapp.auth.config.AdminBootstrapToken;
import com.socialapp.auth.dto.RegisterRequest;
import com.socialapp.auth.entity.Account;
import com.socialapp.auth.repository.AccountRepository;
import com.socialapp.auth.repository.RefreshTokenRepository;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.UserRegisteredEvent;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private static final String SECRET = "test-bootstrap-secret-with-at-least-32-bytes";
    private final RegisterRequest request = new RegisterRequest("admin@example.test", "password", "Admin", null, null, null);
    private AccountRepository accounts;
    private KafkaTemplate<String, Object> kafka;
    private AuthService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        accounts = mock(AccountRepository.class);
        kafka = mock(KafkaTemplate.class);
        service = new AuthService(accounts, mock(RefreshTokenRepository.class), mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class), kafka, new AdminBootstrapToken(SECRET));
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void allowSave() {
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId("bootstrap-account");
            return account;
        });
    }

    @Test
    void validSecretCreatesAdminWithUserRole() {
        allowSave();
        assertThat(service.bootstrapAdmin(request, SECRET).accountId()).isEqualTo("bootstrap-account");
        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(account.capture());
        assertThat(account.getValue().getRoles()).containsExactly("USER", "ADMIN");
        verify(kafka).send(eq(KafkaTopics.USER_REGISTERED), any(UserRegisteredEvent.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong", "test-bootstrap-secret-with-at-least-32-byteS"})
    void invalidSecretNeverTouchesDatabase(String token) {
        assertThatThrownBy(() -> service.bootstrapAdmin(request, token)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(accounts, kafka);
    }

    @Test
    void existingAdminPreventsReplay() {
        when(accounts.countByRole("ADMIN")).thenReturn(1L);
        assertThatThrownBy(() -> service.bootstrapAdmin(request, SECRET)).isInstanceOf(ConflictException.class);
        verify(accounts, never()).save(any());
        verifyNoInteractions(kafka);
    }

    @Test
    void existingEmailCannotBePromotedByBootstrap() {
        when(accounts.existsByEmail(request.email())).thenReturn(true);
        assertThatThrownBy(() -> service.bootstrapAdmin(request, SECRET)).isInstanceOf(ConflictException.class);
        verify(accounts, never()).save(any());
        verifyNoInteractions(kafka);
    }

    @Test
    void transactionCommitPublishesOnlyAfterCommit() {
        allowSave();
        TransactionSynchronizationManager.initSynchronization();
        service.bootstrapAdmin(request, SECRET);
        verifyNoInteractions(kafka);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafka).send(eq(KafkaTopics.USER_REGISTERED), any(UserRegisteredEvent.class));
    }

    @Test
    void transactionRollbackDoesNotPublishPhantomUser() {
        allowSave();
        TransactionSynchronizationManager.initSynchronization();
        service.bootstrapAdmin(request, SECRET);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(kafka);
    }
}
