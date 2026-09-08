package com.socialapp.user.service;

import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.event.FriendRequestEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.FriendshipResponse;
import com.socialapp.user.dto.FriendshipStatusResponse;
import com.socialapp.user.dto.RelationshipStatus;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.entity.Block;
import com.socialapp.user.entity.Friendship;
import com.socialapp.user.entity.FriendshipStatus;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.BlockRepository;
import com.socialapp.user.repository.FriendshipRepository;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FriendshipService — repositories and Kafka are mocked, so these
 * exercise only the service's own decisions (who may act, duplicate/blocked
 * rejection, and the side effects of blocking/unfriending).
 */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FollowService followService;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private FriendshipService friendshipService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        friendshipService = new FriendshipService(
                friendshipRepository, blockRepository, userProfileRepository, kafkaTemplate, followService);
    }

    private Friendship friendship(String id, String requesterId, String addresseeId, FriendshipStatus status) {
        return Friendship.builder()
                .id(id)
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    // ---- sendFriendRequest ----

    @Test
    void sendFriendRequest_success_createsPendingRowAndPublishesEvent() {
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.empty());
        when(blockRepository.existsEitherDirection("user-1", "user-2")).thenReturn(false);
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendshipResponse response = friendshipService.sendFriendRequest("user-1", "user-2");

        assertThat(response.requesterId()).isEqualTo("user-1");
        assertThat(response.addresseeId()).isEqualTo("user-2");
        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);

        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FriendshipStatus.PENDING);

        ArgumentCaptor<FriendRequestEvent> eventCaptor = ArgumentCaptor.forClass(FriendRequestEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.FRIEND_REQUEST), eventCaptor.capture());
        assertThat(eventCaptor.getValue().requesterId()).isEqualTo("user-1");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo("user-2");
        assertThat(eventCaptor.getValue().status()).isEqualTo("PENDING");
    }

    @Test
    void sendFriendRequest_toSelf_throwsConflictAndNeverSaves() {
        assertThatThrownBy(() -> friendshipService.sendFriendRequest("user-1", "user-1"))
                .isInstanceOf(ConflictException.class);

        verify(friendshipRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendFriendRequest_existingFriendship_throwsConflictAndNeverSaves() {
        when(friendshipRepository.findAnyBetween("user-1", "user-2"))
                .thenReturn(Optional.of(friendship("f-1", "user-1", "user-2", FriendshipStatus.ACCEPTED)));

        assertThatThrownBy(() -> friendshipService.sendFriendRequest("user-1", "user-2"))
                .isInstanceOf(ConflictException.class);

        verify(friendshipRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendFriendRequest_blockExists_throwsConflictAndNeverSaves() {
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.empty());
        when(blockRepository.existsEitherDirection("user-1", "user-2")).thenReturn(true);

        assertThatThrownBy(() -> friendshipService.sendFriendRequest("user-1", "user-2"))
                .isInstanceOf(ConflictException.class);

        verify(friendshipRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendFriendRequest_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(null, "user-2"))
                .isInstanceOf(UnauthorizedException.class);

        verify(friendshipRepository, never()).save(any());
    }

    // ---- accept ----

    @Test
    void accept_byAddressee_success_publishesAcceptedEvent() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendshipResponse response = friendshipService.accept("user-2", "f-1");

        assertThat(response.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(pending.getRespondedAt()).isNotNull();

        ArgumentCaptor<FriendRequestEvent> eventCaptor = ArgumentCaptor.forClass(FriendRequestEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.FRIEND_REQUEST), eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo("ACCEPTED");

        verify(followService).autoFollowBothDirections("user-1", "user-2");
    }

    @Test
    void accept_byRequester_throwsForbiddenAndNeverSaves() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> friendshipService.accept("user-1", "f-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(friendshipRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void accept_byUnrelatedUser_throwsForbidden() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> friendshipService.accept("stranger", "f-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void accept_notFound_throwsResourceNotFound() {
        when(friendshipRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.accept("user-2", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void accept_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.accept(null, "f-1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- decline ----

    @Test
    void decline_byAddressee_success_deletesRowAndPublishesDeclinedEvent() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));

        FriendshipResponse response = friendshipService.decline("user-2", "f-1");

        assertThat(response.status()).isEqualTo(FriendshipStatus.DECLINED);
        verify(friendshipRepository).delete(pending);

        ArgumentCaptor<FriendRequestEvent> eventCaptor = ArgumentCaptor.forClass(FriendRequestEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.FRIEND_REQUEST), eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo("DECLINED");
    }

    @Test
    void decline_byRequester_cancelsOwnPendingRequest() {
        // The requester withdrawing their own sent request goes through the same
        // decline() path — same outcome (row deleted), different caller.
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));

        FriendshipResponse response = friendshipService.decline("user-1", "f-1");

        assertThat(response.status()).isEqualTo(FriendshipStatus.DECLINED);
        verify(friendshipRepository).delete(pending);
    }

    @Test
    void decline_byUnrelatedUser_throwsForbiddenAndNeverDeletes() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findById("f-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> friendshipService.decline("user-3", "f-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }

    @Test
    void decline_notFound_throwsResourceNotFound() {
        when(friendshipRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.decline("user-2", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void decline_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.decline(null, "f-1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- removeFriend ----

    @Test
    void removeFriend_existingFriendship_deletesIt() {
        Friendship accepted = friendship("f-1", "user-1", "user-2", FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAcceptedBetween("user-1", "user-2")).thenReturn(Optional.of(accepted));

        friendshipService.removeFriend("user-1", "user-2");

        verify(friendshipRepository).delete(accepted);
    }

    @Test
    void removeFriend_noFriendship_throwsResourceNotFoundAndNeverDeletes() {
        when(friendshipRepository.findAcceptedBetween("user-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.removeFriend("user-1", "user-2"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void removeFriend_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.removeFriend(null, "user-2"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- listFriends ----

    @Test
    void listFriends_returnsPaginatedProfilesOfOtherParty() {
        Pageable pageable = PageRequest.of(0, 10);
        Friendship f1 = friendship("f-1", "user-1", "friend-a", FriendshipStatus.ACCEPTED);
        Friendship f2 = friendship("f-2", "friend-b", "user-1", FriendshipStatus.ACCEPTED);
        Page<Friendship> page = new PageImpl<>(List.of(f1, f2), pageable, 2);
        when(friendshipRepository.findAcceptedFriendships("user-1", pageable)).thenReturn(page);

        UserProfile profileA = UserProfile.builder().id("friend-a").fullName("Friend A")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        UserProfile profileB = UserProfile.builder().id("friend-b").fullName("Friend B")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(userProfileRepository.findByIdIn(List.of("friend-a", "friend-b"))).thenReturn(List.of(profileA, profileB));

        PageResponse<UserProfileResponse> response = friendshipService.listFriends("user-1", pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content()).extracting(UserProfileResponse::id).containsExactlyInAnyOrder("friend-a", "friend-b");
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    void listFriends_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.listFriends(null, PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- listFriendRequests ----

    @Test
    void listFriendRequests_returnsPendingRequestsForAddressee() {
        Pageable pageable = PageRequest.of(0, 10);
        Friendship pending = friendship("f-1", "user-2", "user-1", FriendshipStatus.PENDING);
        Page<Friendship> page = new PageImpl<>(List.of(pending), pageable, 1);
        when(friendshipRepository.findByAddresseeIdAndStatus("user-1", FriendshipStatus.PENDING, pageable)).thenReturn(page);

        PageResponse<FriendshipResponse> response = friendshipService.listFriendRequests("user-1", pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).requesterId()).isEqualTo("user-2");
    }

    @Test
    void listFriendRequests_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.listFriendRequests(null, PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- friendIds ----

    @Test
    void friendIds_returnsOtherPartyOfEachAcceptedFriendship() {
        Friendship f1 = friendship("f-1", "user-1", "friend-a", FriendshipStatus.ACCEPTED);
        Friendship f2 = friendship("f-2", "friend-b", "user-1", FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAcceptedFriendships("user-1")).thenReturn(List.of(f1, f2));

        List<String> ids = friendshipService.friendIds("user-1");

        assertThat(ids).containsExactlyInAnyOrder("friend-a", "friend-b");
    }

    // ---- relationshipStatus ----

    @Test
    void relationshipStatus_noRow_returnsNone() {
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.empty());

        FriendshipStatusResponse status = friendshipService.relationshipStatus("user-1", "user-2");

        assertThat(status.status()).isEqualTo(RelationshipStatus.NONE);
        assertThat(status.friendshipId()).isNull();
    }

    @Test
    void relationshipStatus_acceptedRow_returnsFriends() {
        Friendship accepted = friendship("f-1", "user-1", "user-2", FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.of(accepted));

        FriendshipStatusResponse status = friendshipService.relationshipStatus("user-1", "user-2");

        assertThat(status.status()).isEqualTo(RelationshipStatus.FRIENDS);
        assertThat(status.friendshipId()).isEqualTo("f-1");
    }

    @Test
    void relationshipStatus_callerIsRequesterOfPending_returnsPendingSent() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.of(pending));

        FriendshipStatusResponse status = friendshipService.relationshipStatus("user-1", "user-2");

        assertThat(status.status()).isEqualTo(RelationshipStatus.PENDING_SENT);
    }

    @Test
    void relationshipStatus_callerIsAddresseeOfPending_returnsPendingReceived() {
        Friendship pending = friendship("f-1", "user-1", "user-2", FriendshipStatus.PENDING);
        when(friendshipRepository.findAnyBetween("user-2", "user-1")).thenReturn(Optional.of(pending));

        FriendshipStatusResponse status = friendshipService.relationshipStatus("user-2", "user-1");

        assertThat(status.status()).isEqualTo(RelationshipStatus.PENDING_RECEIVED);
    }

    // ---- block ----

    @Test
    void block_noExistingBlock_createsBlockAndRemovesExistingFriendship() {
        Friendship existingFriendship = friendship("f-1", "user-1", "user-2", FriendshipStatus.ACCEPTED);
        when(blockRepository.existsByBlockerIdAndBlockedId("user-1", "user-2")).thenReturn(false);
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.of(existingFriendship));

        friendshipService.block("user-1", "user-2");

        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(blockCaptor.capture());
        assertThat(blockCaptor.getValue().getBlockerId()).isEqualTo("user-1");
        assertThat(blockCaptor.getValue().getBlockedId()).isEqualTo("user-2");

        verify(friendshipRepository).delete(existingFriendship);
    }

    @Test
    void block_alreadyBlocked_doesNotDuplicateBlockRowButStillRemovesFriendship() {
        Friendship existingFriendship = friendship("f-1", "user-1", "user-2", FriendshipStatus.ACCEPTED);
        when(blockRepository.existsByBlockerIdAndBlockedId("user-1", "user-2")).thenReturn(true);
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.of(existingFriendship));

        friendshipService.block("user-1", "user-2");

        verify(blockRepository, never()).save(any());
        verify(friendshipRepository).delete(existingFriendship);
    }

    @Test
    void block_noExistingFriendship_onlyCreatesBlockRow() {
        when(blockRepository.existsByBlockerIdAndBlockedId("user-1", "user-2")).thenReturn(false);
        when(friendshipRepository.findAnyBetween("user-1", "user-2")).thenReturn(Optional.empty());

        friendshipService.block("user-1", "user-2");

        verify(blockRepository).save(any(Block.class));
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void block_self_throwsConflictAndNeverPersists() {
        assertThatThrownBy(() -> friendshipService.block("user-1", "user-1"))
                .isInstanceOf(ConflictException.class);

        verify(blockRepository, never()).save(any());
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void block_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.block(null, "user-2"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- unblock ----

    @Test
    void unblock_existingBlock_deletesIt() {
        Block block = Block.builder().id("b-1").blockerId("user-1").blockedId("user-2").createdAt(Instant.now()).build();
        when(blockRepository.findByBlockerIdAndBlockedId("user-1", "user-2")).thenReturn(Optional.of(block));

        friendshipService.unblock("user-1", "user-2");

        verify(blockRepository, times(1)).delete(block);
    }

    @Test
    void unblock_noExistingBlock_isNoOp() {
        when(blockRepository.findByBlockerIdAndBlockedId("user-1", "user-2")).thenReturn(Optional.empty());

        friendshipService.unblock("user-1", "user-2");

        verify(blockRepository, never()).delete(any());
    }

    @Test
    void unblock_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.unblock(null, "user-2"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- listBlocked ----

    @Test
    void listBlocked_returnsPaginatedProfilesOfBlockedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        Block block = Block.builder().id("b-1").blockerId("user-1").blockedId("blocked-a").createdAt(Instant.now()).build();
        Page<Block> page = new PageImpl<>(List.of(block), pageable, 1);
        when(blockRepository.findByBlockerId("user-1", pageable)).thenReturn(page);

        UserProfile blockedProfile = UserProfile.builder().id("blocked-a").fullName("Blocked User")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(userProfileRepository.findByIdIn(List.of("blocked-a"))).thenReturn(List.of(blockedProfile));

        PageResponse<UserProfileResponse> response = friendshipService.listBlocked("user-1", pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo("blocked-a");
    }

    @Test
    void listBlocked_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> friendshipService.listBlocked(null, PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
