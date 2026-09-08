package com.socialapp.user.service;

import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.FriendSuggestionResponse;
import com.socialapp.user.entity.Block;
import com.socialapp.user.entity.Friendship;
import com.socialapp.user.entity.FriendshipStatus;
import com.socialapp.user.entity.FriendSuggestionDismissal;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.BlockRepository;
import com.socialapp.user.repository.FriendSuggestionDismissalRepository;
import com.socialapp.user.repository.FriendshipRepository;
import com.socialapp.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FriendSuggestionService: exclusion rules (self/friends/
 * pending/blocked), mutual-friend ranking, the small-pool fallback that
 * still shows candidates with zero mutual friends, and the adaptive
 * dismiss-TTL (short cooldown when the candidate pool is small).
 */
@ExtendWith(MockitoExtension.class)
class FriendSuggestionServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FriendSuggestionDismissalRepository dismissalRepository;

    private FriendSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new FriendSuggestionService(
                friendshipRepository, blockRepository, userProfileRepository, dismissalRepository);
    }

    private Friendship accepted(String requesterId, String addresseeId) {
        return Friendship.builder().id(requesterId + "-" + addresseeId)
                .requesterId(requesterId).addresseeId(addresseeId)
                .status(FriendshipStatus.ACCEPTED).createdAt(Instant.now()).build();
    }

    private Friendship pending(String requesterId, String addresseeId) {
        return Friendship.builder().id(requesterId + "-" + addresseeId)
                .requesterId(requesterId).addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING).createdAt(Instant.now()).build();
    }

    private UserProfile profile(String id, Instant createdAt) {
        return UserProfile.builder().id(id).fullName("User " + id).createdAt(createdAt).build();
    }

    @Test
    void suggestions_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> service.suggestions(null, 10)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void suggestions_excludesSelfFriendsPendingAndBlocked() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of(accepted("me", "friend-1")));
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of(pending("me", "pending-1")));
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me"))
                .thenReturn(List.of(Block.builder().id("b1").blockerId("me").blockedId("blocked-1").build()));
        when(friendshipRepository.findAcceptedFriendships("friend-1")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("friend-1", Instant.now()),
                profile("pending-1", Instant.now()),
                profile("blocked-1", Instant.now()),
                profile("stranger-1", Instant.now())));
        when(dismissalRepository.findActiveDismissedIds(eq("me"), any(Instant.class))).thenReturn(List.of());

        List<FriendSuggestionResponse> result = service.suggestions("me", 10);

        assertThat(result).extracting(r -> r.profile().id()).containsExactly("stranger-1");
    }

    @Test
    void suggestions_ranksByMutualFriendCountDescending() {
        when(friendshipRepository.findAcceptedFriendships("me"))
                .thenReturn(List.of(accepted("me", "f1"), accepted("me", "f2")));
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        // candidate-a is friends with both f1 and f2 (mutual=2); candidate-b only with f1 (mutual=1)
        when(friendshipRepository.findAcceptedFriendships("f1"))
                .thenReturn(List.of(accepted("f1", "candidate-a"), accepted("f1", "candidate-b")));
        when(friendshipRepository.findAcceptedFriendships("f2"))
                .thenReturn(List.of(accepted("f2", "candidate-a")));
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("f1", Instant.now()),
                profile("f2", Instant.now()),
                profile("candidate-a", Instant.now()),
                profile("candidate-b", Instant.now())));
        when(dismissalRepository.findActiveDismissedIds(eq("me"), any(Instant.class))).thenReturn(List.of());

        List<FriendSuggestionResponse> result = service.suggestions("me", 10);

        assertThat(result).extracting(r -> r.profile().id()).containsExactly("candidate-a", "candidate-b");
        assertThat(result.get(0).mutualFriendCount()).isEqualTo(2);
        assertThat(result.get(1).mutualFriendCount()).isEqualTo(1);
    }

    @Test
    void suggestions_smallPoolWithNoMutualFriends_stillReturnsOtherUsers() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("stranger-1", Instant.now()),
                profile("stranger-2", Instant.now())));
        when(dismissalRepository.findActiveDismissedIds(eq("me"), any(Instant.class))).thenReturn(List.of());

        List<FriendSuggestionResponse> result = service.suggestions("me", 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.mutualFriendCount() == 0);
    }

    @Test
    void suggestions_activelyDismissed_isExcludedUntilItsOwnFallbackKicksIn() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("stranger-1", Instant.now()),
                profile("stranger-2", Instant.now())));
        when(dismissalRepository.findActiveDismissedIds(eq("me"), any(Instant.class)))
                .thenReturn(List.of("stranger-1"));

        List<FriendSuggestionResponse> result = service.suggestions("me", 10);

        assertThat(result).extracting(r -> r.profile().id()).containsExactly("stranger-2");
    }

    @Test
    void suggestions_everyCandidateDismissed_fallsBackToShowingThemAnywayRatherThanEmpty() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("stranger-1", Instant.now())));
        when(dismissalRepository.findActiveDismissedIds(eq("me"), any(Instant.class)))
                .thenReturn(List.of("stranger-1"));

        List<FriendSuggestionResponse> result = service.suggestions("me", 10);

        assertThat(result).extracting(r -> r.profile().id()).containsExactly("stranger-1");
    }

    @Test
    void dismiss_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> service.dismiss(null, "target")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void dismiss_largeCandidatePool_usesLongTtl() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        List<UserProfile> manyUsers = new java.util.ArrayList<>();
        manyUsers.add(profile("me", Instant.now()));
        for (int i = 0; i < 20; i++) {
            manyUsers.add(profile("stranger-" + i, Instant.now()));
        }
        when(userProfileRepository.findAll()).thenReturn(manyUsers);
        when(dismissalRepository.findByUserIdAndSuggestedUserId("me", "stranger-0")).thenReturn(Optional.empty());
        when(dismissalRepository.save(any(FriendSuggestionDismissal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.dismiss("me", "stranger-0");

        ArgumentCaptor<FriendSuggestionDismissal> captor = ArgumentCaptor.forClass(FriendSuggestionDismissal.class);
        verify(dismissalRepository).save(captor.capture());
        Duration ttl = Duration.between(captor.getValue().getDismissedAt(), captor.getValue().getExpiresAt());
        assertThat(ttl).isCloseTo(Duration.ofDays(14), Duration.ofMinutes(1));
    }

    @Test
    void dismiss_smallCandidatePool_usesShortTtl() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()),
                profile("stranger-1", Instant.now())));
        when(dismissalRepository.findByUserIdAndSuggestedUserId("me", "stranger-1")).thenReturn(Optional.empty());
        when(dismissalRepository.save(any(FriendSuggestionDismissal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.dismiss("me", "stranger-1");

        ArgumentCaptor<FriendSuggestionDismissal> captor = ArgumentCaptor.forClass(FriendSuggestionDismissal.class);
        verify(dismissalRepository).save(captor.capture());
        Duration ttl = Duration.between(captor.getValue().getDismissedAt(), captor.getValue().getExpiresAt());
        assertThat(ttl).isCloseTo(Duration.ofHours(36), Duration.ofMinutes(1));
    }

    @Test
    void dismiss_existingDismissal_refreshesExpiryInstead_ofDuplicating() {
        when(friendshipRepository.findAcceptedFriendships("me")).thenReturn(List.of());
        when(friendshipRepository.findPendingInvolving("me")).thenReturn(List.of());
        when(blockRepository.findByBlockerIdOrBlockedId("me", "me")).thenReturn(List.of());
        when(userProfileRepository.findAll()).thenReturn(List.of(
                profile("me", Instant.now()), profile("stranger-1", Instant.now())));
        FriendSuggestionDismissal existing = FriendSuggestionDismissal.builder()
                .id("d1").userId("me").suggestedUserId("stranger-1")
                .dismissedAt(Instant.now().minus(Duration.ofDays(10)))
                .expiresAt(Instant.now().minus(Duration.ofDays(1)))
                .build();
        when(dismissalRepository.findByUserIdAndSuggestedUserId("me", "stranger-1"))
                .thenReturn(Optional.of(existing));
        when(dismissalRepository.save(any(FriendSuggestionDismissal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.dismiss("me", "stranger-1");

        verify(dismissalRepository, never()).save(argThat(d -> !"d1".equals(d.getId())));
        verify(dismissalRepository).save(argThat(d -> d.getExpiresAt().isAfter(Instant.now())));
    }
}
