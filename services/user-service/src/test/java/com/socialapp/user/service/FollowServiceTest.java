package com.socialapp.user.service;

import com.socialapp.common.event.FollowEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.FollowStatusResponse;
import com.socialapp.user.entity.Follow;
import com.socialapp.user.repository.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FollowService — a follow graph deliberately kept separate
 * from Friendship: follow/unfollow can happen independently of friend
 * status, only follow (not unfollow) publishes a Kafka event, and
 * autoFollowBothDirections (called from FriendshipService.accept) is
 * idempotent and silent.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private FollowService followService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        followService = new FollowService(followRepository, kafkaTemplate);
    }

    @Test
    void follow_newFollow_savesAndPublishesEvent() {
        when(followRepository.existsByFollowerIdAndFolloweeId("user-1", "user-2")).thenReturn(false);

        followService.follow("user-1", "user-2");

        verify(followRepository).save(any(Follow.class));
        ArgumentCaptor<FollowEvent> captor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.FOLLOW), captor.capture());
        assertThat(captor.getValue().followerId()).isEqualTo("user-1");
        assertThat(captor.getValue().followeeId()).isEqualTo("user-2");
    }

    @Test
    void follow_alreadyFollowing_isIdempotent_noSaveNoEvent() {
        when(followRepository.existsByFollowerIdAndFolloweeId("user-1", "user-2")).thenReturn(true);

        followService.follow("user-1", "user-2");

        verify(followRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void follow_self_throwsConflict() {
        assertThatThrownBy(() -> followService.follow("user-1", "user-1"))
                .isInstanceOf(ConflictException.class);
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> followService.follow(null, "user-2"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unfollow_existingFollow_deletesAndPublishesNoEvent() {
        Follow existing = Follow.builder().id("f-1").followerId("user-1").followeeId("user-2").build();
        when(followRepository.findByFollowerIdAndFolloweeId("user-1", "user-2"))
                .thenReturn(java.util.Optional.of(existing));

        followService.unfollow("user-1", "user-2");

        verify(followRepository).delete(existing);
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void unfollow_notFollowing_isNoOp() {
        when(followRepository.findByFollowerIdAndFolloweeId("user-1", "user-2"))
                .thenReturn(java.util.Optional.empty());

        followService.unfollow("user-1", "user-2");

        verify(followRepository, never()).delete(any(Follow.class));
    }

    @Test
    void status_reflectsFollowingFlagAndCounts() {
        when(followRepository.existsByFollowerIdAndFolloweeId("user-1", "user-2")).thenReturn(true);
        when(followRepository.countByFolloweeId("user-2")).thenReturn(5L);
        when(followRepository.countByFollowerId("user-2")).thenReturn(3L);

        FollowStatusResponse status = followService.status("user-1", "user-2");

        assertThat(status.following()).isTrue();
        assertThat(status.followerCount()).isEqualTo(5L);
        assertThat(status.followingCount()).isEqualTo(3L);
    }

    @Test
    void status_anonymousCaller_followingIsFalseWithoutQuerying() {
        when(followRepository.countByFolloweeId("user-2")).thenReturn(0L);
        when(followRepository.countByFollowerId("user-2")).thenReturn(0L);

        FollowStatusResponse status = followService.status(null, "user-2");

        assertThat(status.following()).isFalse();
        verify(followRepository, never()).existsByFollowerIdAndFolloweeId(any(), any());
    }

    @Test
    void autoFollowBothDirections_createsBothMissingFollowsWithoutEvents() {
        when(followRepository.existsByFollowerIdAndFolloweeId("user-1", "user-2")).thenReturn(false);
        when(followRepository.existsByFollowerIdAndFolloweeId("user-2", "user-1")).thenReturn(false);

        followService.autoFollowBothDirections("user-1", "user-2");

        verify(followRepository, org.mockito.Mockito.times(2)).save(any(Follow.class));
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void autoFollowBothDirections_oneDirectionAlreadyExists_onlyCreatesMissingOne() {
        when(followRepository.existsByFollowerIdAndFolloweeId("user-1", "user-2")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId("user-2", "user-1")).thenReturn(false);

        followService.autoFollowBothDirections("user-1", "user-2");

        verify(followRepository, org.mockito.Mockito.times(1)).save(any(Follow.class));
    }
}
