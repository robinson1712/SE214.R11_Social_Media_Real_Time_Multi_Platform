package com.socialapp.chat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class WebSocketOriginTest {
    @Test
    void launcherEnvironmentControlsAllowedOrigins() throws Exception {
        String expression = WebSocketConfig.class.getDeclaredField("allowedOrigins")
                .getAnnotation(Value.class).value();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CORS_ALLOWED_ORIGINS", "https://frontend.example.test");
        WebSocketConfig config = new WebSocketConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", environment.resolveRequiredPlaceholders(expression));
        String[] origins = ReflectionTestUtils.invokeMethod(config, "originPatterns");
        assertThat(origins).containsExactly("https://frontend.example.test");
    }

    @Test
    void rejectsWildcardSubdomainPatterns() {
        WebSocketConfig config = new WebSocketConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://*.example.test");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(config, "originPatterns"))
                .isInstanceOf(IllegalStateException.class);
    }
}
