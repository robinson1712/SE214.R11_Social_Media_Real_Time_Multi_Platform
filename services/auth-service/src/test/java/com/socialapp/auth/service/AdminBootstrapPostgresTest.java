package com.socialapp.auth.service;

import com.socialapp.auth.config.AdminBootstrapToken;
import com.socialapp.auth.dto.RegisterRequest;
import com.socialapp.auth.repository.AccountRepository;
import com.socialapp.auth.repository.RefreshTokenRepository;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.UserRegisteredEvent;
import com.socialapp.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses a disposable native PostgreSQL database, never the application's auth_db. */
@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${AUTH_TEST_POSTGRES_URL:jdbc:postgresql://127.0.0.1:5432/auth_bootstrap_test}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.admin-bootstrap-token=postgres-test-bootstrap-secret-at-least-32-bytes"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_POSTGRES_TESTS", matches = "true")
@Import({AuthService.class, AdminBootstrapToken.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminBootstrapPostgresTest {
    @Autowired private AuthService service;
    @Autowired private AccountRepository accounts;
    @Autowired private RefreshTokenRepository refreshTokens;
    @MockBean private PasswordEncoder encoder;
    @MockBean private JwtTokenProvider tokens;
    @MockBean private KafkaTemplate<String, Object> kafka;

    @Test
    void concurrentBootstrapCommitsExactlyOneAdminAndPublishesOnlyThatUser() throws Exception {
        CountDownLatch bothPassedEmptyAdminCheck = new CountDownLatch(2);
        when(encoder.encode(anyString())).thenAnswer(invocation -> {
            bothPassedEmptyAdminCheck.countDown();
            if (!bothPassedEmptyAdminCheck.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Both transactions did not reach the bootstrap race");
            }
            return "test-password-hash";
        });
        when(tokens.generateAccessToken(anyString(), anyList())).thenReturn("test-access-token");
        when(tokens.generateRefreshToken(anyString())).thenAnswer(invocation -> "refresh-" + invocation.getArgument(0));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attempt("first@example.test"));
            var second = executor.submit(() -> attempt("second@example.test"));
            assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(accounts.countByRole("ADMIN")).isEqualTo(1);
            assertThat(refreshTokens.count()).isEqualTo(1);
            verify(kafka, times(1)).send(eq(KafkaTopics.USER_REGISTERED), any(UserRegisteredEvent.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private int attempt(String email) {
        try {
            service.bootstrapAdmin(new RegisterRequest(email, "password", "Admin", null, null, null),
                    "postgres-test-bootstrap-secret-at-least-32-bytes");
            return 1;
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && "40001".equals(sql.getSQLState())) {
                    return 0;
                }
            }
            throw failure;
        }
    }
}
