package com.socialapp.user.controller;

import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerBindingTest {
    private UserProfileService service;
    private MockMvc mvc;
    private final UserProfileResponse profile = new UserProfileResponse(
            "profile-123", "Binding regression", null, null, null,
            null, null, null, null, true, null, null);

    @BeforeEach
    void setUp() {
        service = mock(UserProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void resolvesUnnamedPathVariableFromCompiledParameterName() throws Exception {
        when(service.getProfile("profile-123")).thenReturn(profile);
        mvc.perform(get("/api/users/profile-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("profile-123"));
        verify(service).getProfile("profile-123");
    }

    @Test
    void resolvesUnnamedQueryParameterFromCompiledParameterName() throws Exception {
        when(service.search(eq("Binding"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        mvc.perform(get("/api/users/search").param("q", "Binding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value("profile-123"));
        verify(service).search(eq("Binding"), any(Pageable.class));
    }
}
