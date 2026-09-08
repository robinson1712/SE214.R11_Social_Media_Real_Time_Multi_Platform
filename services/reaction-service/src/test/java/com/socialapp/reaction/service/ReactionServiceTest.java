package com.socialapp.reaction.service;

import com.socialapp.common.enums.ReactionType;
import com.socialapp.common.enums.TargetType;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reaction.dto.ReactionSummaryResponse;
import com.socialapp.reaction.dto.UpsertReactionRequest;
import com.socialapp.reaction.entity.Reaction;
import com.socialapp.reaction.repository.ReactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReactionService. The repository and Kafka publishing are
 * mocked; CurrentUserContext is set/cleared per test via the test-support
 * seam in common-lib rather than a real HTTP request.
 */
@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ReactionService reactionService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        reactionService = new ReactionService(reactionRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Reaction existingReaction(String id, String userId, ReactionType type) {
        return Reaction.builder()
                .id(id)
                .targetType(TargetType.POST)
                .targetId("post-1")
                .targetOwnerId("post-owner-1")
                .userId(userId)
                .type(type)
                .build();
    }

    @Test
    void upsert_noExistingReaction_createsNewRowAndPublishesNotRemoved() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        UpsertReactionRequest request = new UpsertReactionRequest(TargetType.POST, "post-1", "post-owner-1", ReactionType.LIKE);
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.empty());
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Reaction saved = reactionService.upsert(request);

        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getType()).isEqualTo(ReactionType.LIKE);
        assertThat(saved.getTargetId()).isEqualTo("post-1");
        assertThat(saved.getTargetOwnerId()).isEqualTo("post-owner-1");

        ArgumentCaptor<ReactionEvent> captor = ArgumentCaptor.forClass(ReactionEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REACTION), eq("post-1"), captor.capture());
        ReactionEvent event = captor.getValue();
        assertThat(event.removed()).isFalse();
        assertThat(event.reactionType()).isEqualTo("LIKE");
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.targetType()).isEqualTo("POST");
        assertThat(event.targetId()).isEqualTo("post-1");
    }

    @Test
    void upsert_existingReaction_updatesTypeInPlaceRatherThanCreatingNewRow() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        Reaction existing = existingReaction("reaction-1", "user-1", ReactionType.LIKE);
        UpsertReactionRequest request = new UpsertReactionRequest(TargetType.POST, "post-1", "post-owner-1", ReactionType.LOVE);
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.of(existing));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Reaction saved = reactionService.upsert(request);

        assertThat(saved.getId()).isEqualTo("reaction-1");
        assertThat(saved.getType()).isEqualTo(ReactionType.LOVE);

        ArgumentCaptor<Reaction> captor = ArgumentCaptor.forClass(Reaction.class);
        verify(reactionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("reaction-1");

        ArgumentCaptor<ReactionEvent> eventCaptor = ArgumentCaptor.forClass(ReactionEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REACTION), eq("post-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().removed()).isFalse();
        assertThat(eventCaptor.getValue().reactionType()).isEqualTo("LOVE");
    }

    @Test
    void upsert_missingTargetType_throwsBadRequestAndNeverSaves() {
        UpsertReactionRequest request = new UpsertReactionRequest(null, "post-1", "post-owner-1", ReactionType.LIKE);

        assertThatThrownBy(() -> reactionService.upsert(request))
                .isInstanceOf(BadRequestException.class);

        verify(reactionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(String.class), any(), any());
    }

    @Test
    void upsert_missingTargetId_throwsBadRequest() {
        UpsertReactionRequest request = new UpsertReactionRequest(TargetType.POST, null, "post-owner-1", ReactionType.LIKE);

        assertThatThrownBy(() -> reactionService.upsert(request))
                .isInstanceOf(BadRequestException.class);

        verify(reactionRepository, never()).save(any());
    }

    @Test
    void upsert_missingType_throwsBadRequest() {
        UpsertReactionRequest request = new UpsertReactionRequest(TargetType.POST, "post-1", "post-owner-1", null);

        assertThatThrownBy(() -> reactionService.upsert(request))
                .isInstanceOf(BadRequestException.class);

        verify(reactionRepository, never()).save(any());
    }

    @Test
    void delete_existingReaction_removesAndPublishesRemoved() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        Reaction existing = existingReaction("reaction-1", "user-1", ReactionType.LIKE);
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.of(existing));

        reactionService.delete(TargetType.POST, "post-1");

        verify(reactionRepository).delete(existing);

        ArgumentCaptor<ReactionEvent> captor = ArgumentCaptor.forClass(ReactionEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REACTION), eq("post-1"), captor.capture());
        assertThat(captor.getValue().removed()).isTrue();
        assertThat(captor.getValue().reactionId()).isEqualTo("reaction-1");
    }

    @Test
    void delete_noExistingReaction_isNoOpAndDoesNotPublish() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.empty());

        reactionService.delete(TargetType.POST, "post-1");

        verify(reactionRepository, never()).delete(any(Reaction.class));
        verify(kafkaTemplate, never()).send(any(String.class), any(), any());
    }

    @Test
    void getMyReaction_present_returnsIt() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        Reaction existing = existingReaction("reaction-1", "user-1", ReactionType.WOW);
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.of(existing));

        Optional<Reaction> result = reactionService.getMyReaction(TargetType.POST, "post-1");

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(ReactionType.WOW);
    }

    @Test
    void getMyReaction_absent_returnsEmpty() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(reactionRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.empty());

        Optional<Reaction> result = reactionService.getMyReaction(TargetType.POST, "post-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getSummary_aggregatesCountsByTypeAndComputesTotal() {
        ReactionRepository.TypeCount likeCount = typeCount(ReactionType.LIKE, 5L);
        ReactionRepository.TypeCount loveCount = typeCount(ReactionType.LOVE, 2L);
        when(reactionRepository.countByTarget(TargetType.POST, "post-1")).thenReturn(List.of(likeCount, loveCount));

        ReactionSummaryResponse summary = reactionService.getSummary(TargetType.POST, "post-1");

        assertThat(summary.total()).isEqualTo(7L);
        assertThat(summary.counts()).containsEntry(ReactionType.LIKE, 5L);
        assertThat(summary.counts()).containsEntry(ReactionType.LOVE, 2L);
        assertThat(summary.counts()).doesNotContainKey(ReactionType.SAD);
    }

    @Test
    void getSummary_noReactions_returnsEmptyCountsAndZeroTotal() {
        when(reactionRepository.countByTarget(TargetType.POST, "post-1")).thenReturn(List.of());

        ReactionSummaryResponse summary = reactionService.getSummary(TargetType.POST, "post-1");

        assertThat(summary.total()).isZero();
        assertThat(summary.counts()).isEmpty();
    }

    private ReactionRepository.TypeCount typeCount(ReactionType type, long count) {
        return new ReactionRepository.TypeCount() {
            @Override
            public ReactionType getType() {
                return type;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
