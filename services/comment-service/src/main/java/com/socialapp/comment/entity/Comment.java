package com.socialapp.comment.entity;

import com.socialapp.common.enums.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** What this comment is on — POST or REEL today; comment-service used to be
     * hardcoded to posts (a "postId" column) until reels needed comments too,
     * so this mirrors reaction-service's TargetType/targetId/targetOwnerId
     * shape rather than special-casing a second content type. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType;

    @Column(nullable = false)
    private String targetId;

    @Column(nullable = false)
    private String authorId;

    @Column(nullable = false)
    private String targetOwnerId;

    private String parentCommentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Instant createdAt;

    private Instant updatedAt;

    @Builder.Default
    private boolean deleted = false;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
