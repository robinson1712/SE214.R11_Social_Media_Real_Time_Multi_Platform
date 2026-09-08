package com.socialapp.reels.listener;

import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.reels.document.Reel;
import com.socialapp.reels.repository.ReelRepository;
import com.socialapp.reels.service.ReelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReelEventListener — keeps Reel.commentCount/reactionCount in
 * sync with COMMENT_CREATED/REACTION Kafka events. The @KafkaListener methods
 * are invoked directly; no real Kafka broker is involved.
 */
@ExtendWith(MockitoExtension.class)
class ReelEventListenerTest {

    @Mock
    private ReelRepository reelRepository;
    @Mock
    private ReelService reelService;

    private ReelEventListener listener;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        listener = new ReelEventListener(reelRepository, reelService);
    }

    private Reel existingReel(String id) {
        return Reel.builder()
                .id(id)
                .authorId("author-1")
                .videoUrl("http://media/video.mp4")
                .commentCount(0)
                .reactionCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void onCommentCreated_topLevelCommentOnExistingReel_incrementsCommentCount() {
        Reel reel = existingReel("reel-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        CommentCreatedEvent event =
                new CommentCreatedEvent("comment-1", "REEL", "reel-1", "commenter-1", "author-1", null, Instant.now());

        listener.onCommentCreated(event);

        assertThat(reel.getCommentCount()).isEqualTo(1);
        verify(reelRepository).save(reel);
    }

    @Test
    void onCommentCreated_replyComment_isIgnored() {
        CommentCreatedEvent event = new CommentCreatedEvent(
                "comment-2", "REEL", "reel-1", "commenter-1", "author-1", "parent-comment-1", Instant.now());

        listener.onCommentCreated(event);

        verify(reelRepository, never()).findById(any());
        verify(reelRepository, never()).save(any());
    }

    @Test
    void onCommentCreated_reelDoesNotExist_isNoOp() {
        when(reelRepository.findById("missing-reel")).thenReturn(Optional.empty());
        CommentCreatedEvent event = new CommentCreatedEvent(
                "comment-1", "REEL", "missing-reel", "commenter-1", "author-1", null, Instant.now());

        listener.onCommentCreated(event);

        verify(reelRepository, never()).save(any());
    }

    @Test
    void onCommentCreated_forNonReelTarget_isIgnored() {
        CommentCreatedEvent event = new CommentCreatedEvent(
                "comment-1", "POST", "post-1", "commenter-1", "author-1", null, Instant.now());

        listener.onCommentCreated(event);

        verify(reelRepository, never()).findById(any());
        verify(reelRepository, never()).save(any());
    }

    @Test
    void onReaction_forReelTarget_notRemoved_incrementsReactionCount() {
        Reel reel = existingReel("reel-1");
        reel.setReactionCount(2);
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        ReactionEvent event = new ReactionEvent("reaction-1", "REEL", "reel-1", "author-1", "user-1", "LIKE", false, Instant.now());

        listener.onReaction(event);

        assertThat(reel.getReactionCount()).isEqualTo(3);
        verify(reelRepository).save(reel);
    }

    @Test
    void onReaction_forReelTarget_removed_decrementsReactionCount() {
        Reel reel = existingReel("reel-1");
        reel.setReactionCount(2);
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        ReactionEvent event = new ReactionEvent("reaction-1", "REEL", "reel-1", "author-1", "user-1", "LIKE", true, Instant.now());

        listener.onReaction(event);

        assertThat(reel.getReactionCount()).isEqualTo(1);
        verify(reelRepository).save(reel);
    }

    @Test
    void onReaction_removedAtZero_clampsAtZeroInsteadOfGoingNegative() {
        Reel reel = existingReel("reel-1");
        reel.setReactionCount(0);
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        ReactionEvent event = new ReactionEvent("reaction-1", "REEL", "reel-1", "author-1", "user-1", "LIKE", true, Instant.now());

        listener.onReaction(event);

        assertThat(reel.getReactionCount()).isZero();
        verify(reelRepository).save(reel);
    }

    @Test
    void onReaction_forNonReelTarget_isIgnored() {
        ReactionEvent event = new ReactionEvent("reaction-1", "POST", "post-1", "author-1", "user-1", "LIKE", false, Instant.now());

        listener.onReaction(event);

        verify(reelRepository, never()).findById(any());
        verify(reelRepository, never()).save(any());
    }

    @Test
    void onReaction_reelDoesNotExist_isNoOp() {
        when(reelRepository.findById("missing-reel")).thenReturn(Optional.empty());
        ReactionEvent event = new ReactionEvent("reaction-1", "REEL", "missing-reel", "author-1", "user-1", "LIKE", false, Instant.now());

        listener.onReaction(event);

        verify(reelRepository, never()).save(any());
    }

    @Test
    void onContentRemoved_forReelTarget_delegatesToReelService() {
        ContentRemovedEvent event = new ContentRemovedEvent("REEL", "reel-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(reelService).removeForModeration("reel-1");
    }

    @Test
    void onContentRemoved_forNonReelTarget_isIgnored() {
        ContentRemovedEvent event = new ContentRemovedEvent("POST", "post-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(reelService, never()).removeForModeration(any());
    }
}
