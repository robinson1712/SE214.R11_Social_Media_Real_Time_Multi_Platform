package com.socialapp.fanpage.service;

import com.socialapp.fanpage.entity.AdminRole;
import com.socialapp.fanpage.entity.PageAdmin;
import com.socialapp.fanpage.repository.FanpageRepository;
import com.socialapp.fanpage.repository.PageAdminRepository;
import com.socialapp.fanpage.repository.PageFollowerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanpageServicePostAuthorizationTest {

    @Mock FanpageRepository fanpageRepository;
    @Mock PageFollowerRepository pageFollowerRepository;
    @Mock PageAdminRepository pageAdminRepository;
    @Mock org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks FanpageService fanpageService;

    @Test
    void eachCurrentManagerRoleMayCreatePost() {
        when(fanpageRepository.existsById("page-1")).thenReturn(true);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "manager-1"))
                .thenReturn(Optional.of(manager(AdminRole.OWNER)), Optional.of(manager(AdminRole.ADMIN)),
                        Optional.of(manager(AdminRole.EDITOR)));

        for (AdminRole role : AdminRole.values()) {
            assertThat(fanpageService.canCreatePost("page-1", "manager-1")).as(role.name()).isTrue();
        }
    }

    @Test
    void followerOrRemovedManagerCannotCreatePagePost() {
        when(fanpageRepository.existsById("page-1")).thenReturn(true);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "follower-1")).thenReturn(Optional.empty());

        assertThat(fanpageService.canCreatePost("page-1", "follower-1")).isFalse();
    }

    @Test
    void missingPageCannotCreatePost() {
        when(fanpageRepository.existsById("missing")).thenReturn(false);

        assertThat(fanpageService.canCreatePost("missing", "manager-1")).isFalse();
    }

    private PageAdmin manager(AdminRole role) {
        return PageAdmin.builder().pageId("page-1").userId("manager-1").role(role).build();
    }
}
