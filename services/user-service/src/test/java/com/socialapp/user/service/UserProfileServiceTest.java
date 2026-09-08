package com.socialapp.user.service;

import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.UpdateProfileRequest;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.entity.Gender;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserProfileService — the repository is mocked, so these exercise
 * only the service's own decisions (auth checks, not-found handling, partial updates).
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository);
    }

    private UserProfile profile(String id) {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        return UserProfile.builder()
                .id(id)
                .fullName("Alice Original")
                .bio("Original bio")
                .dob(LocalDate.of(1995, 5, 20))
                .gender(Gender.FEMALE)
                .location("Hanoi")
                .createdAt(past)
                .updatedAt(past)
                .build();
    }

    @Test
    void getProfile_found_returnsResponse() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(profile("user-1")));

        UserProfileResponse response = userProfileService.getProfile("user-1");

        assertThat(response.id()).isEqualTo("user-1");
        assertThat(response.fullName()).isEqualTo("Alice Original");
    }

    @Test
    void getProfile_notFound_throwsResourceNotFound() {
        when(userProfileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMe_authenticatedKnownUser_returnsResponse() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(profile("user-1")));

        UserProfileResponse response = userProfileService.getMe("user-1");

        assertThat(response.id()).isEqualTo("user-1");
    }

    @Test
    void getMe_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> userProfileService.getMe(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getMe_noProfileForCurrentUser_throwsResourceNotFound() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMe("user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMe_partialUpdate_onlyNonNullFieldsChangeAndUpdatedAtBumped() {
        UserProfile existing = profile("user-1");
        Instant originalUpdatedAt = existing.getUpdatedAt();
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only fullName and location provided; bio, dob, gender left null (unchanged).
        UpdateProfileRequest request =
                new UpdateProfileRequest("Alice Updated", null, null, null, "Da Nang", null, null);

        UserProfileResponse response = userProfileService.updateMe("user-1", request);

        assertThat(response.fullName()).isEqualTo("Alice Updated");
        assertThat(response.location()).isEqualTo("Da Nang");
        // Unchanged fields retain their original values.
        assertThat(response.bio()).isEqualTo("Original bio");
        assertThat(response.dob()).isEqualTo(LocalDate.of(1995, 5, 20));
        assertThat(response.gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);

        verify(userProfileRepository).save(existing);
    }

    @Test
    void updateMe_allFieldsProvided_allFieldsChange() {
        UserProfile existing = profile("user-1");
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
                "New Name", "New bio", LocalDate.of(2000, 1, 1), Gender.MALE, "Saigon", null, false);

        UserProfileResponse response = userProfileService.updateMe("user-1", request);

        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.bio()).isEqualTo("New bio");
        assertThat(response.dob()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(response.gender()).isEqualTo(Gender.MALE);
        assertThat(response.location()).isEqualTo("Saigon");
        assertThat(response.readReceiptsEnabled()).isFalse();
    }

    @Test
    void updateMe_readReceiptsOmitted_leavesExistingValueUnchanged() {
        UserProfile existing = profile("user-1");
        existing.setReadReceiptsEnabled(false);
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request =
                new UpdateProfileRequest("Alice Updated", null, null, null, null, null, null);

        UserProfileResponse response = userProfileService.updateMe("user-1", request);

        assertThat(response.readReceiptsEnabled()).isFalse();
    }

    @Test
    void updateMe_notAuthenticated_throwsUnauthorizedAndNeverSaves() {
        UpdateProfileRequest request = new UpdateProfileRequest("X", null, null, null, null, null, null);

        assertThatThrownBy(() -> userProfileService.updateMe(null, request))
                .isInstanceOf(UnauthorizedException.class);

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateMe_profileNotFound_throwsResourceNotFound() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());
        UpdateProfileRequest request = new UpdateProfileRequest("X", null, null, null, null, null, null);

        assertThatThrownBy(() -> userProfileService.updateMe("user-1", request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateAvatar_authenticated_updatesAvatarUrlAndBumpsUpdatedAt() {
        UserProfile existing = profile("user-1");
        Instant originalUpdatedAt = existing.getUpdatedAt();
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userProfileService.updateAvatar("user-1", "https://cdn.example.com/avatar.png");

        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void updateAvatar_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> userProfileService.updateAvatar(null, "https://cdn.example.com/avatar.png"))
                .isInstanceOf(UnauthorizedException.class);

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateAvatar_profileNotFound_throwsResourceNotFound() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateAvatar("user-1", "https://cdn.example.com/avatar.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCover_authenticated_updatesCoverUrlAndBumpsUpdatedAt() {
        UserProfile existing = profile("user-1");
        Instant originalUpdatedAt = existing.getUpdatedAt();
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userProfileService.updateCover("user-1", "https://cdn.example.com/cover.png");

        assertThat(response.coverUrl()).isEqualTo("https://cdn.example.com/cover.png");
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void updateCover_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> userProfileService.updateCover(null, "https://cdn.example.com/cover.png"))
                .isInstanceOf(UnauthorizedException.class);

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateCover_profileNotFound_throwsResourceNotFound() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateCover("user-1", "https://cdn.example.com/cover.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMe_savedEntityCaptured_hasExpectedFullNameAndBio() {
        UserProfile existing = profile("user-1");
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);

        userProfileService.updateMe(
                "user-1", new UpdateProfileRequest("Captured Name", null, null, null, null, null, null));

        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Captured Name");
        assertThat(captor.getValue().getBio()).isEqualTo("Original bio");
    }

    @Test
    void search_matchingQuery_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserProfile> page = new PageImpl<>(List.of(profile("user-1")), pageable, 1);
        when(userProfileRepository.findByFullNameContainingIgnoreCase("Alice", pageable)).thenReturn(page);

        Page<UserProfileResponse> result = userProfileService.search("Alice", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).fullName()).isEqualTo("Alice Original");
    }

    @Test
    void search_blankQuery_throwsBadRequestAndNeverQueriesRepository() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> userProfileService.search("   ", pageable))
                .isInstanceOf(BadRequestException.class);

        verify(userProfileRepository, never()).findByFullNameContainingIgnoreCase(any(), any());
    }

    @Test
    void search_nullQuery_throwsBadRequest() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> userProfileService.search(null, pageable))
                .isInstanceOf(BadRequestException.class);
    }
}
