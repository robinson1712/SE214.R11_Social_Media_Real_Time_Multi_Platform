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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final UserProfileRepository userProfileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FollowService followService;

    @Transactional
    public FriendshipResponse sendFriendRequest(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        if (currentUserId.equals(targetId)) {
            throw new ConflictException("Cannot send a friend request to yourself");
        }
        if (friendshipRepository.findAnyBetween(currentUserId, targetId).isPresent()) {
            throw new ConflictException("A friendship already exists between these users");
        }
        if (blockRepository.existsEitherDirection(currentUserId, targetId)) {
            throw new ConflictException("Cannot send a friend request; a block exists between these users");
        }

        Friendship friendship = Friendship.builder()
                .requesterId(currentUserId)
                .addresseeId(targetId)
                .status(FriendshipStatus.PENDING)
                .build();
        friendship = friendshipRepository.save(friendship);

        publish(friendship);
        return FriendshipResponse.from(friendship);
    }

    @Transactional
    public FriendshipResponse accept(String currentUserId, String friendshipId) {
        requireAuth(currentUserId);
        Friendship friendship = findFriendshipOrThrow(friendshipId);
        if (!friendship.getAddresseeId().equals(currentUserId)) {
            throw new ForbiddenException("Only the addressee may accept this friend request");
        }
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(Instant.now());
        friendship = friendshipRepository.save(friendship);
        publish(friendship);
        followService.autoFollowBothDirections(friendship.getRequesterId(), friendship.getAddresseeId());
        return FriendshipResponse.from(friendship);
    }

    /**
     * Declines an incoming request (called by the addressee) or cancels one
     * you sent (called by the requester) — same outcome either way: the
     * pending row goes away. The row is deleted rather than marked DECLINED
     * so a declined/cancelled request doesn't permanently block either side
     * from sending a fresh one later (findAnyBetween would otherwise treat
     * a dead DECLINED row the same as an active PENDING/ACCEPTED one).
     */
    @Transactional
    public FriendshipResponse decline(String currentUserId, String friendshipId) {
        requireAuth(currentUserId);
        Friendship friendship = findFriendshipOrThrow(friendshipId);
        boolean isParty = friendship.getAddresseeId().equals(currentUserId)
                || friendship.getRequesterId().equals(currentUserId);
        if (!isParty) {
            throw new ForbiddenException("Only a party to this friend request may decline or cancel it");
        }
        friendship.setStatus(FriendshipStatus.DECLINED);
        friendship.setRespondedAt(Instant.now());
        publish(friendship);
        FriendshipResponse response = FriendshipResponse.from(friendship);
        friendshipRepository.delete(friendship);
        return response;
    }

    /** The caller's relationship to targetId — see {@link RelationshipStatus} for what each value means. */
    public FriendshipStatusResponse relationshipStatus(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        return friendshipRepository.findAnyBetween(currentUserId, targetId)
                .map(f -> {
                    if (f.getStatus() == FriendshipStatus.ACCEPTED) {
                        return new FriendshipStatusResponse(RelationshipStatus.FRIENDS, f.getId());
                    }
                    RelationshipStatus status = f.getRequesterId().equals(currentUserId)
                            ? RelationshipStatus.PENDING_SENT
                            : RelationshipStatus.PENDING_RECEIVED;
                    return new FriendshipStatusResponse(status, f.getId());
                })
                .orElse(new FriendshipStatusResponse(RelationshipStatus.NONE, null));
    }

    @Transactional
    public void removeFriend(String currentUserId, String friendId) {
        requireAuth(currentUserId);
        Friendship friendship = friendshipRepository.findAcceptedBetween(currentUserId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("No friendship found between these users"));
        friendshipRepository.delete(friendship);
    }

    public PageResponse<UserProfileResponse> listFriends(String currentUserId, Pageable pageable) {
        requireAuth(currentUserId);
        Page<Friendship> page = friendshipRepository.findAcceptedFriendships(currentUserId, pageable);
        List<String> friendIds = page.getContent().stream()
                .map(f -> otherParty(f, currentUserId))
                .collect(Collectors.toList());
        Map<String, UserProfile> profilesById = userProfileRepository.findByIdIn(friendIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, p -> p));
        List<UserProfileResponse> content = friendIds.stream()
                .map(profilesById::get)
                .filter(java.util.Objects::nonNull)
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
        Page<UserProfileResponse> responsePage = new PageImpl<>(content, pageable, page.getTotalElements());
        return PageResponse.from(responsePage);
    }

    public PageResponse<FriendshipResponse> listFriendRequests(String currentUserId, Pageable pageable) {
        requireAuth(currentUserId);
        Page<Friendship> page = friendshipRepository.findByAddresseeIdAndStatus(currentUserId, FriendshipStatus.PENDING, pageable);
        Page<FriendshipResponse> responsePage = page.map(FriendshipResponse::from);
        return PageResponse.from(responsePage);
    }

    public List<String> friendIds(String userId) {
        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> otherParty(f, userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void block(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        if (currentUserId.equals(targetId)) {
            throw new ConflictException("Cannot block yourself");
        }
        if (!blockRepository.existsByBlockerIdAndBlockedId(currentUserId, targetId)) {
            Block block = Block.builder()
                    .blockerId(currentUserId)
                    .blockedId(targetId)
                    .build();
            blockRepository.save(block);
        }
        friendshipRepository.findAnyBetween(currentUserId, targetId).ifPresent(friendshipRepository::delete);
    }

    @Transactional
    public void unblock(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        blockRepository.findByBlockerIdAndBlockedId(currentUserId, targetId)
                .ifPresent(blockRepository::delete);
    }

    public PageResponse<UserProfileResponse> listBlocked(String currentUserId, Pageable pageable) {
        requireAuth(currentUserId);
        Page<Block> page = blockRepository.findByBlockerId(currentUserId, pageable);
        List<String> blockedIds = page.getContent().stream().map(Block::getBlockedId).collect(Collectors.toList());
        Map<String, UserProfile> profilesById = userProfileRepository.findByIdIn(blockedIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, p -> p));
        List<UserProfileResponse> content = blockedIds.stream()
                .map(profilesById::get)
                .filter(java.util.Objects::nonNull)
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
        Page<UserProfileResponse> responsePage = new PageImpl<>(content, pageable, page.getTotalElements());
        return PageResponse.from(responsePage);
    }

    private String otherParty(Friendship f, String userId) {
        return f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
    }

    private Friendship findFriendshipOrThrow(String id) {
        return friendshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found: " + id));
    }

    private void requireAuth(String currentUserId) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
    }

    private void publish(Friendship friendship) {
        kafkaTemplate.send(KafkaTopics.FRIEND_REQUEST, new FriendRequestEvent(
                friendship.getRequesterId(), friendship.getAddresseeId(), friendship.getStatus().name(), Instant.now()));
    }
}
