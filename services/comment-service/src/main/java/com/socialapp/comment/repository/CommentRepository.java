package com.socialapp.comment.repository;

import com.socialapp.comment.entity.Comment;
import com.socialapp.common.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, String> {

    Page<Comment> findByTargetTypeAndTargetIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtDesc(
            TargetType targetType, String targetId, Pageable pageable);

    Page<Comment> findByParentCommentIdAndTargetTypeAndTargetIdAndDeletedFalseOrderByCreatedAtAsc(
            String parentCommentId, TargetType targetType, String targetId, Pageable pageable);

    Optional<Comment> findByIdAndDeletedFalse(String id);
}
