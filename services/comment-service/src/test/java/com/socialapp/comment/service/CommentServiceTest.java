package com.socialapp.comment.service;

import com.socialapp.comment.dto.CreateCommentRequest;
import com.socialapp.comment.dto.UpdateCommentRequest;
import com.socialapp.comment.entity.Comment;
import com.socialapp.comment.repository.CommentRepository;
import com.socialapp.common.enums.TargetType;
import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
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
 * Unit tests for CommentService. Kafka publishing is mocked; CurrentUserContext
 * is set/cleared per test via the test-support seam in common-lib rather than a
 * real HTTP request. The service no longer calls out to post-service (the
 * caller supplies targetOwnerId directly, same simplification reaction-service
 * already made — see Reaction.java), so there's no Feign client left to mock.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        commentService = new CommentService(commentRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Comment existingComment(String id, String authorId, String targetId) {
        return Comment.builder()
                .id(id)
                .targetType(TargetType.POST)
                .targetId(targetId)
                .authorId(authorId)
                .targetOwnerId("target-owner-1")
                .content("original content")
                .build();
    }

    @Test
    void createComment_topLevel_savesAndPublishesEvent() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateCommentRequest request =
                new CreateCommentRequest(TargetType.POST, "post-1", "post-owner-1", "Nice post!", null);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment saved = commentService.createComment(request);

        assertThat(saved.getAuthorId()).isEqualTo("author-1");
        assertThat(saved.getTargetOwnerId()).isEqualTo("post-owner-1");
        assertThat(saved.getTargetType()).isEqualTo(TargetType.POST);
        assertThat(saved.getTargetId()).isEqualTo("post-1");
        assertThat(saved.getParentCommentId()).isNull();

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.COMMENT_CREATED), eq(saved.getId()), captor.capture());
        CommentCreatedEvent event = captor.getValue();
        assertThat(event.commentId()).isEqualTo(saved.getId());
        assertThat(event.targetType()).isEqualTo("POST");
        assertThat(event.targetId()).isEqualTo("post-1");
        assertThat(event.authorId()).isEqualTo("author-1");
        assertThat(event.targetOwnerId()).isEqualTo("post-owner-1");
        assertThat(event.parentCommentId()).isNull();
    }

    @Test
    void createComment_onReel_savesWithReelTargetType() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateCommentRequest request =
                new CreateCommentRequest(TargetType.REEL, "reel-1", "reel-owner-1", "Nice reel!", null);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment saved = commentService.createComment(request);

        assertThat(saved.getTargetType()).isEqualTo(TargetType.REEL);
        assertThat(saved.getTargetId()).isEqualTo("reel-1");

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.COMMENT_CREATED), eq(saved.getId()), captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo("REEL");
    }

    @Test
    void createComment_profaneContent_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateCommentRequest request =
                new CreateCommentRequest(TargetType.POST, "post-1", "post-owner-1", "you fucking idiot", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_reply_setsParentCommentIdOnEntityAndEvent() {
        CurrentUserContext.setForTests("author-2", List.of("USER"));
        CreateCommentRequest request = new CreateCommentRequest(
                TargetType.POST, "post-1", "post-owner-1", "A reply", "parent-comment-1");
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment saved = commentService.createComment(request);

        assertThat(saved.getParentCommentId()).isEqualTo("parent-comment-1");

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.COMMENT_CREATED), eq(saved.getId()), captor.capture());
        assertThat(captor.getValue().parentCommentId()).isEqualTo("parent-comment-1");
    }

    @Test
    void createComment_missingTargetType_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(null, "post-1", "post-owner-1", "content", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_blankTargetId_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(TargetType.POST, "  ", "post-owner-1", "content", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_nullTargetId_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(TargetType.POST, null, "post-owner-1", "content", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_blankTargetOwnerId_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(TargetType.POST, "post-1", "  ", "content", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_blankContent_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(TargetType.POST, "post-1", "post-owner-1", "   ", null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_nullContent_throwsBadRequest() {
        CreateCommentRequest request = new CreateCommentRequest(TargetType.POST, "post-1", "post-owner-1", null, null);

        assertThatThrownBy(() -> commentService.createComment(request))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void removeForModeration_existingComment_softDeletesIt() {
        Comment comment = existingComment("c-1", "author-1", "post-1");
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.removeForModeration("c-1");

        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository).save(comment);
    }

    @Test
    void removeForModeration_missingComment_isNoOp() {
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());

        commentService.removeForModeration("missing");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_byAuthor_updatesContent() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment updated = commentService.updateComment("comment-1", new UpdateCommentRequest("edited content"));

        assertThat(updated.getContent()).isEqualTo("edited content");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateComment_editingInProfanity_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(existingComment("c-1", "author-1", "post-1")));

        assertThatThrownBy(() -> commentService.updateComment("c-1", new UpdateCommentRequest("you bitch")))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_byNonAuthor_throwsForbiddenAndNeverSaves() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment("comment-1", new UpdateCommentRequest("hacked")))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_missing_throwsResourceNotFound() {
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.updateComment("missing", new UpdateCommentRequest("x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateComment_alreadyDeleted_throwsResourceNotFound() {
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        comment.setDeleted(true);
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment("comment-1", new UpdateCommentRequest("x")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_byAuthor_softDeletesAndStillSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        commentService.deleteComment("comment-1");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getId()).isEqualTo("comment-1");
        verify(commentRepository, never()).delete(any(Comment.class));
        verify(commentRepository, never()).deleteById(any());
    }

    @Test
    void deleteComment_byNonAuthor_throwsForbiddenAndNeverSaves() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment("comment-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_missing_throwsResourceNotFound() {
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteComment_alreadyDeleted_throwsResourceNotFound() {
        Comment comment = existingComment("comment-1", "author-1", "post-1");
        comment.setDeleted(true);
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment("comment-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }
}
