package com.socialapp.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class AdminBootstrapTokenTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void absentConfigurationDisablesBootstrap(String configured) {
        AdminBootstrapToken token = new AdminBootstrapToken(configured);
        assertThat(token.matches(configured)).isFalse();
        assertThat(token.matches("anything")).isFalse();
    }

    @Test
    void shortConfigurationFailsStartupWithoutEchoingSecret() {
        assertThatThrownBy(() -> new AdminBootstrapToken("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void acceptsExactTokenOnly() {
        String configured = "0123456789abcdef0123456789abcdef";
        AdminBootstrapToken token = new AdminBootstrapToken(configured);
        assertThat(token.matches(configured)).isTrue();
        assertThat(token.matches(configured.toUpperCase())).isFalse();
        assertThat(token.matches(configured + "x")).isFalse();
        assertThat(token.matches(null)).isFalse();
    }
}
