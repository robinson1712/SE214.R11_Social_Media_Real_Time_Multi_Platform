package com.socialapp.user.service;

import com.socialapp.common.event.FollowEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.FollowStatusResponse;
import com.socialapp.user.entity.Follow;
import com.socialapp.user.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * A follow graph independent of friendship: you can follow someone (see
 * their public posts surface more) without being their friend, and you can
 * unfollow a friend without unfriending them. Friendship and follow are
 * kept in sync in one direction only — accepting a friend request
 * auto-follows both ways (see {@link #autoFollowBothDirections}) — but
 * unfollowing never touches the friendship, and removing a friendship never
 * touches follows.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void follow(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        if (currentUserId.equals(targetId)) {
            throw new ConflictException("Cannot follow yourself");
        }
        if (followRepository.existsByFollowerIdAndFolloweeId(currentUserId, targetId)) {
            return;
        }
        followRepository.save(Follow.builder().followerId(currentUserId).followeeId(targetId).build());
        kafkaTemplate.send(KafkaTopics.FOLLOW, new FollowEvent(currentUserId, targetId, Instant.now()));
    }

    @Transactional
    public void unfollow(String currentUserId, String targetId) {
        requireAuth(currentUserId);
        followRepository.findByFollowerIdAndFolloweeId(currentUserId, targetId)
                .ifPresent(followRepository::delete);
        // Deliberately no event — an unfollow notification would be a strange, slightly hostile
        // thing to surface (per the requested design: follow notifies, unfollow doesn't).
    }

    public FollowStatusResponse status(String currentUserId, String targetId) {
        boolean following = currentUserId != null
                && followRepository.existsByFollowerIdAndFolloweeId(currentUserId, targetId);
        long followerCount = followRepository.countByFolloweeId(targetId);
        long followingCount = followRepository.countByFollowerId(targetId);
        return new FollowStatusResponse(following, followerCount, followingCount);
    }

    /** Called from FriendshipService when a friend request is accepted — silent (no Kafka event/notification), since this is an implicit side effect of friending, not an explicit follow action. */
    @Transactional
    public void autoFollowBothDirections(String userA, String userB) {
        if (!followRepository.existsByFollowerIdAndFolloweeId(userA, userB)) {
            followRepository.save(Follow.builder().followerId(userA).followeeId(userB).build());
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(userB, userA)) {
            followRepository.save(Follow.builder().followerId(userB).followeeId(userA).build());
        }
    }

    private void requireAuth(String currentUserId) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
    }
}
