package com.socialapp.fanpage.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.PageEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.fanpage.dto.AddAdminRequest;
import com.socialapp.fanpage.dto.CreateFanpageRequest;
import com.socialapp.fanpage.entity.AdminRole;
import com.socialapp.fanpage.entity.Fanpage;
import com.socialapp.fanpage.entity.PageAdmin;
import com.socialapp.fanpage.entity.PageFollower;
import com.socialapp.fanpage.repository.FanpageRepository;
import com.socialapp.fanpage.repository.PageAdminRepository;
import com.socialapp.fanpage.repository.PageFollowerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FanpageService. Repositories and Kafka are mocked so these
 * exercise only the service's own decisions (follower-count bookkeeping,
 * admin gating, and the last-owner removal guard).
 */
@ExtendWith(MockitoExtension.class)
class FanpageServiceTest {

    @Mock
    private FanpageRepository fanpageRepository;
    @Mock
    private PageFollowerRepository pageFollowerRepository;
    @Mock
    private PageAdminRepository pageAdminRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private FanpageService fanpageService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        fanpageService = new FanpageService(fanpageRepository, pageFollowerRepository, pageAdminRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Fanpage existingPage(String id, String ownerId, int followerCount) {
        return Fanpage.builder()
                .id(id)
                .name("Test Page")
                .category("General")
                .description("desc")
                .ownerId(ownerId)
                .followerCount(followerCount)
                .build();
    }

    private PageAdmin existingAdmin(String pageId, String userId, AdminRole role) {
        return PageAdmin.builder()
                .pageId(pageId)
                .userId(userId)
                .role(role)
                .build();
    }

    // ---------- createPage ----------

    @Test
    void createPage_savesPageAndOwnerAdminAndPublishesEvent() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateFanpageRequest request = new CreateFanpageRequest("My Page", "Brand", "desc");
        when(fanpageRepository.save(any(Fanpage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pageAdminRepository.save(any(PageAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

        Fanpage saved = fanpageService.createPage(request);

        assertThat(saved.getOwnerId()).isEqualTo("owner-1");
        assertThat(saved.getFollowerCount()).isZero();

        ArgumentCaptor<PageAdmin> adminCaptor = ArgumentCaptor.forClass(PageAdmin.class);
        verify(pageAdminRepository).save(adminCaptor.capture());
        PageAdmin owner = adminCaptor.getValue();
        assertThat(owner.getPageId()).isEqualTo(saved.getId());
        assertThat(owner.getUserId()).isEqualTo("owner-1");
        assertThat(owner.getRole()).isEqualTo(AdminRole.OWNER);

        ArgumentCaptor<PageEvent> eventCaptor = ArgumentCaptor.forClass(PageEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAGE), eventCaptor.capture());
        assertThat(eventCaptor.getValue().pageId()).isEqualTo(saved.getId());
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("owner-1");
        assertThat(eventCaptor.getValue().type()).isEqualTo("CREATED");
    }

    @Test
    void createPage_noAuthenticatedCaller_throwsUnauthorized() {
        CreateFanpageRequest request = new CreateFanpageRequest("My Page", "Brand", "desc");

        assertThatThrownBy(() -> fanpageService.createPage(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(fanpageRepository, never()).save(any());
        verify(pageAdminRepository, never()).save(any());
    }

    @Test
    void createPage_profaneName_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateFanpageRequest request = new CreateFanpageRequest("fucking idiots page", "Brand", "desc");

        assertThatThrownBy(() -> fanpageService.createPage(request))
                .isInstanceOf(BadRequestException.class);

        verify(fanpageRepository, never()).save(any());
        verify(pageAdminRepository, never()).save(any());
    }

    @Test
    void createPage_profaneDescription_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateFanpageRequest request = new CreateFanpageRequest("My Page", "Brand", "you fucking idiot");

        assertThatThrownBy(() -> fanpageService.createPage(request))
                .isInstanceOf(BadRequestException.class);

        verify(fanpageRepository, never()).save(any());
        verify(pageAdminRepository, never()).save(any());
    }

    // ---------- getPage ----------

    @Test
    void getPage_found_returnsIt() {
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(existingPage("page-1", "owner-1", 0)));

        Fanpage found = fanpageService.getPage("page-1");

        assertThat(found.getId()).isEqualTo("page-1");
    }

    @Test
    void getPage_missing_throwsResourceNotFound() {
        when(fanpageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fanpageService.getPage("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- listPages ----------

    @Test
    void listPages_withNameFilter_delegatesToNameSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fanpageRepository.findByNameContainingIgnoreCase("brand", pageable)).thenReturn(new PageImpl<>(List.of()));

        fanpageService.listPages("brand", pageable);

        verify(fanpageRepository).findByNameContainingIgnoreCase("brand", pageable);
        verify(fanpageRepository, never()).findAll(pageable);
    }

    @Test
    void listPages_nullName_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fanpageRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        fanpageService.listPages(null, pageable);

        verify(fanpageRepository).findAll(pageable);
        verify(fanpageRepository, never()).findByNameContainingIgnoreCase(anyString(), eq(pageable));
    }

    @Test
    void listPages_blankName_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fanpageRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        fanpageService.listPages("   ", pageable);

        verify(fanpageRepository).findAll(pageable);
        verify(fanpageRepository, never()).findByNameContainingIgnoreCase(anyString(), eq(pageable));
    }

    // ---------- follow ----------

    @Test
    void follow_notYetFollowing_createsFollowerAndIncrementsCountAndPublishes() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        Fanpage page = existingPage("page-1", "owner-1", 5);
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(page));
        when(pageFollowerRepository.existsByPageIdAndUserId("page-1", "follower-1")).thenReturn(false);
        when(pageFollowerRepository.save(any(PageFollower.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fanpageRepository.save(any(Fanpage.class))).thenAnswer(inv -> inv.getArgument(0));

        PageFollower follower = fanpageService.follow("page-1");

        assertThat(follower.getPageId()).isEqualTo("page-1");
        assertThat(follower.getUserId()).isEqualTo("follower-1");

        ArgumentCaptor<Fanpage> pageCaptor = ArgumentCaptor.forClass(Fanpage.class);
        verify(fanpageRepository).save(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getFollowerCount()).isEqualTo(6);

        ArgumentCaptor<PageEvent> eventCaptor = ArgumentCaptor.forClass(PageEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAGE), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("FOLLOWED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("follower-1");
    }

    @Test
    void follow_alreadyFollowing_throwsConflictAndNeverPersistsOrPublishes() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        Fanpage page = existingPage("page-1", "owner-1", 5);
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(page));
        when(pageFollowerRepository.existsByPageIdAndUserId("page-1", "follower-1")).thenReturn(true);

        assertThatThrownBy(() -> fanpageService.follow("page-1"))
                .isInstanceOf(ConflictException.class);

        verify(pageFollowerRepository, never()).save(any());
        verify(fanpageRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    // ---------- unfollow ----------

    @Test
    void unfollow_following_removesFollowerAndDecrementsCount() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        PageFollower follower = PageFollower.builder().pageId("page-1").userId("follower-1").build();
        when(pageFollowerRepository.findByPageIdAndUserId("page-1", "follower-1")).thenReturn(Optional.of(follower));
        Fanpage page = existingPage("page-1", "owner-1", 5);
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(page));
        when(fanpageRepository.save(any(Fanpage.class))).thenAnswer(inv -> inv.getArgument(0));

        fanpageService.unfollow("page-1");

        verify(pageFollowerRepository).delete(follower);
        ArgumentCaptor<Fanpage> pageCaptor = ArgumentCaptor.forClass(Fanpage.class);
        verify(fanpageRepository).save(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getFollowerCount()).isEqualTo(4);
    }

    @Test
    void unfollow_atZeroCount_clampsAtZero() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        PageFollower follower = PageFollower.builder().pageId("page-1").userId("follower-1").build();
        when(pageFollowerRepository.findByPageIdAndUserId("page-1", "follower-1")).thenReturn(Optional.of(follower));
        Fanpage page = existingPage("page-1", "owner-1", 0);
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(page));
        when(fanpageRepository.save(any(Fanpage.class))).thenAnswer(inv -> inv.getArgument(0));

        fanpageService.unfollow("page-1");

        ArgumentCaptor<Fanpage> pageCaptor = ArgumentCaptor.forClass(Fanpage.class);
        verify(fanpageRepository).save(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getFollowerCount()).isZero();
    }

    @Test
    void unfollow_notFollowing_throwsResourceNotFound() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        when(pageFollowerRepository.findByPageIdAndUserId("page-1", "follower-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fanpageService.unfollow("page-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(pageFollowerRepository, never()).delete(any());
        verify(fanpageRepository, never()).save(any());
    }

    // ---------- addOrUpdateAdmin ----------

    @Test
    void addOrUpdateAdmin_byOwner_createsNewAdminRow() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(existingPage("page-1", "owner-1", 0)));
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "new-admin-1")).thenReturn(Optional.empty());
        when(pageAdminRepository.save(any(PageAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

        PageAdmin result = fanpageService.addOrUpdateAdmin("page-1", new AddAdminRequest("new-admin-1", AdminRole.EDITOR));

        assertThat(result.getPageId()).isEqualTo("page-1");
        assertThat(result.getUserId()).isEqualTo("new-admin-1");
        assertThat(result.getRole()).isEqualTo(AdminRole.EDITOR);
    }

    @Test
    void addOrUpdateAdmin_existingAdminRow_updatesRole() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(existingPage("page-1", "owner-1", 0)));
        PageAdmin existingRow = existingAdmin("page-1", "editor-1", AdminRole.EDITOR);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "editor-1")).thenReturn(Optional.of(existingRow));
        when(pageAdminRepository.save(any(PageAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

        PageAdmin result = fanpageService.addOrUpdateAdmin("page-1", new AddAdminRequest("editor-1", AdminRole.ADMIN));

        assertThat(result).isSameAs(existingRow);
        assertThat(result.getRole()).isEqualTo(AdminRole.ADMIN);
    }

    @Test
    void addOrUpdateAdmin_byEditor_throwsForbidden() {
        CurrentUserContext.setForTests("editor-1", List.of("USER"));
        PageAdmin editorRow = existingAdmin("page-1", "editor-1", AdminRole.EDITOR);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "editor-1")).thenReturn(Optional.of(editorRow));

        assertThatThrownBy(() -> fanpageService.addOrUpdateAdmin("page-1", new AddAdminRequest("target-1", AdminRole.EDITOR)))
                .isInstanceOf(ForbiddenException.class);

        verify(pageAdminRepository, never()).save(any());
    }

    @Test
    void addOrUpdateAdmin_byNonAdmin_throwsForbidden() {
        CurrentUserContext.setForTests("stranger-1", List.of("USER"));
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "stranger-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fanpageService.addOrUpdateAdmin("page-1", new AddAdminRequest("target-1", AdminRole.EDITOR)))
                .isInstanceOf(ForbiddenException.class);

        verify(pageAdminRepository, never()).save(any());
    }

    // ---------- removeAdmin ----------

    @Test
    void removeAdmin_nonOwnerAdmin_removedRegardlessOfOwnerCount() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        PageAdmin targetAdmin = existingAdmin("page-1", "admin-2", AdminRole.ADMIN);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "admin-2")).thenReturn(Optional.of(targetAdmin));

        fanpageService.removeAdmin("page-1", "admin-2");

        verify(pageAdminRepository).delete(targetAdmin);
        verify(pageAdminRepository, never()).countByPageIdAndRole(anyString(), any());
    }

    @Test
    void removeAdmin_lastRemainingOwner_throwsBadRequest() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        when(pageAdminRepository.countByPageIdAndRole("page-1", AdminRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> fanpageService.removeAdmin("page-1", "owner-1"))
                .isInstanceOf(BadRequestException.class);

        verify(pageAdminRepository, never()).delete(any());
    }

    @Test
    void removeAdmin_ownerWithCoOwners_isAllowed() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        PageAdmin secondOwner = existingAdmin("page-1", "owner-2", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-2")).thenReturn(Optional.of(secondOwner));
        when(pageAdminRepository.countByPageIdAndRole("page-1", AdminRole.OWNER)).thenReturn(2L);

        fanpageService.removeAdmin("page-1", "owner-2");

        verify(pageAdminRepository).delete(secondOwner);
    }

    @Test
    void removeAdmin_byEditor_throwsForbidden() {
        CurrentUserContext.setForTests("editor-1", List.of("USER"));
        PageAdmin editorRow = existingAdmin("page-1", "editor-1", AdminRole.EDITOR);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "editor-1")).thenReturn(Optional.of(editorRow));

        assertThatThrownBy(() -> fanpageService.removeAdmin("page-1", "admin-2"))
                .isInstanceOf(ForbiddenException.class);

        verify(pageAdminRepository, never()).delete(any());
    }

    @Test
    void removeAdmin_targetNotAnAdmin_throwsResourceNotFound() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        PageAdmin ownerRow = existingAdmin("page-1", "owner-1", AdminRole.OWNER);
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "owner-1")).thenReturn(Optional.of(ownerRow));
        when(pageAdminRepository.findByPageIdAndUserId("page-1", "nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fanpageService.removeAdmin("page-1", "nobody"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(pageAdminRepository, never()).delete(any());
    }

    // ---------- myManagedPages ----------

    @Test
    void myManagedPages_mapsAdminRowsToPagesAndPreservesTotalElements() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        PageAdmin a1 = existingAdmin("page-1", "admin-1", AdminRole.OWNER);
        PageAdmin a2 = existingAdmin("page-2", "admin-1", AdminRole.ADMIN);
        PageImpl<PageAdmin> adminPage = new PageImpl<>(List.of(a1, a2), pageable, 10);
        when(pageAdminRepository.findByUserId("admin-1", pageable)).thenReturn(adminPage);

        Fanpage page1 = existingPage("page-1", "admin-1", 3);
        Fanpage page2 = existingPage("page-2", "owner-2", 7);
        when(fanpageRepository.findAllById(List.of("page-1", "page-2"))).thenReturn(List.of(page1, page2));

        org.springframework.data.domain.Page<Fanpage> result = fanpageService.myManagedPages(pageable);

        assertThat(result.getContent()).containsExactly(page1, page2);
        assertThat(result.getTotalElements()).isEqualTo(10);
    }

    @Test
    void myManagedPages_pageDeletedAfterAdminRowCreated_isFilteredOutOfResults() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        PageAdmin a1 = existingAdmin("page-1", "admin-1", AdminRole.OWNER);
        PageAdmin a2 = existingAdmin("page-2", "admin-1", AdminRole.ADMIN);
        PageImpl<PageAdmin> adminPage = new PageImpl<>(List.of(a1, a2), pageable, 2);
        when(pageAdminRepository.findByUserId("admin-1", pageable)).thenReturn(adminPage);

        Fanpage page1 = existingPage("page-1", "admin-1", 3);
        when(fanpageRepository.findAllById(List.of("page-1", "page-2"))).thenReturn(List.of(page1));

        org.springframework.data.domain.Page<Fanpage> result = fanpageService.myManagedPages(pageable);

        assertThat(result.getContent()).containsExactly(page1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void myManagedPages_noAuthenticatedCaller_throwsUnauthorized() {
        assertThatThrownBy(() -> fanpageService.myManagedPages(PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- myFollowedPages ----------

    @Test
    void myFollowedPages_mapsFollowerRowsToPagesAndPreservesTotalElements() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        PageFollower f1 = PageFollower.builder().pageId("page-1").userId("follower-1").build();
        PageFollower f2 = PageFollower.builder().pageId("page-2").userId("follower-1").build();
        PageImpl<PageFollower> followerPage = new PageImpl<>(List.of(f1, f2), pageable, 10);
        when(pageFollowerRepository.findByUserId("follower-1", pageable)).thenReturn(followerPage);

        Fanpage page1 = existingPage("page-1", "owner-1", 3);
        Fanpage page2 = existingPage("page-2", "owner-2", 7);
        when(fanpageRepository.findAllById(List.of("page-1", "page-2"))).thenReturn(List.of(page1, page2));

        org.springframework.data.domain.Page<Fanpage> result = fanpageService.myFollowedPages(pageable);

        assertThat(result.getContent()).containsExactly(page1, page2);
        assertThat(result.getTotalElements()).isEqualTo(10);
    }

    @Test
    void myFollowedPages_pageDeletedAfterFollowerRowCreated_isFilteredOutOfResults() {
        CurrentUserContext.setForTests("follower-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        PageFollower f1 = PageFollower.builder().pageId("page-1").userId("follower-1").build();
        PageFollower f2 = PageFollower.builder().pageId("page-2").userId("follower-1").build();
        PageImpl<PageFollower> followerPage = new PageImpl<>(List.of(f1, f2), pageable, 2);
        when(pageFollowerRepository.findByUserId("follower-1", pageable)).thenReturn(followerPage);

        Fanpage page1 = existingPage("page-1", "owner-1", 3);
        when(fanpageRepository.findAllById(List.of("page-1", "page-2"))).thenReturn(List.of(page1));

        org.springframework.data.domain.Page<Fanpage> result = fanpageService.myFollowedPages(pageable);

        assertThat(result.getContent()).containsExactly(page1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void myFollowedPages_noAuthenticatedCaller_throwsUnauthorized() {
        assertThatThrownBy(() -> fanpageService.myFollowedPages(PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- removeForModeration ----------

    @Test
    void removeForModeration_existing_deletesPageAndFollowersAndAdmins() {
        Fanpage page = existingPage("page-1", "owner-1", 5);
        when(fanpageRepository.findById("page-1")).thenReturn(Optional.of(page));

        fanpageService.removeForModeration("page-1");

        verify(pageFollowerRepository).deleteByPageId("page-1");
        verify(pageAdminRepository).deleteByPageId("page-1");
        verify(fanpageRepository).delete(page);
    }

    @Test
    void removeForModeration_missing_isNoOp() {
        when(fanpageRepository.findById("missing")).thenReturn(Optional.empty());

        fanpageService.removeForModeration("missing");

        verify(pageFollowerRepository, never()).deleteByPageId(anyString());
        verify(pageAdminRepository, never()).deleteByPageId(anyString());
        verify(fanpageRepository, never()).delete(any(Fanpage.class));
    }
}
