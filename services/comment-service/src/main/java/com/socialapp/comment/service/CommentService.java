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
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Comment createComment(CreateCommentRequest request) {
        if (request.targetType() == null) {
            throw new BadRequestException("targetType is required");
        }
        if (request.targetId() == null || request.targetId().isBlank()) {
            throw new BadRequestException("targetId is required");
        }
        if (request.targetOwnerId() == null || request.targetOwnerId().isBlank()) {
            throw new BadRequestException("targetOwnerId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestException("content must not be blank");
        }
        rejectIfProfane(request.content());

        String authorId = CurrentUserContext.getUserId();

        Comment comment = Comment.builder()
                .targetType(request.targetType())
                .targetId(request.targetId())
                .authorId(authorId)
                .targetOwnerId(request.targetOwnerId())
                .parentCommentId(request.parentCommentId())
                .content(request.content())
                .build();

        Comment saved = commentRepository.save(comment);

        CommentCreatedEvent event = new CommentCreatedEvent(
                saved.getId(), saved.getTargetType().name(), saved.getTargetId(), saved.getAuthorId(),
                saved.getTargetOwnerId(), saved.getParentCommentId(), Instant.now());
        kafkaTemplate.send(KafkaTopics.COMMENT_CREATED, saved.getId(), event);

        return saved;
    }

    public Page<Comment> getTopLevelComments(TargetType targetType, String targetId, Pageable pageable) {
        return commentRepository.findByTargetTypeAndTargetIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtDesc(
                targetType, targetId, pageable);
    }

    public Page<Comment> getReplies(String parentCommentId, Pageable pageable) {
        return commentRepository.findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(parentCommentId, pageable);
    }

    private Comment getOwnedActiveComment(String id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
        return comment;
    }

    public Comment updateComment(String id, UpdateCommentRequest request) {
        Comment comment = getOwnedActiveComment(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("Only the author can edit this comment");
        }
        rejectIfProfane(request.content());
        comment.setContent(request.content());
        comment.setUpdatedAt(Instant.now());
        return commentRepository.save(comment);
    }

    public void deleteComment(String id) {
        Comment comment = getOwnedActiveComment(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("Only the author can delete this comment");
        }
        comment.setDeleted(true);
        comment.setUpdatedAt(Instant.now());
        commentRepository.save(comment);
    }

    /** Driven by moderation-service's ContentRemovedEvent — idempotent soft-delete. */
    public void removeForModeration(String commentId) {
        commentRepository.findById(commentId).ifPresent(comment -> {
            comment.setDeleted(true);
            comment.setUpdatedAt(Instant.now());
            commentRepository.save(comment);
        });
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }
}
