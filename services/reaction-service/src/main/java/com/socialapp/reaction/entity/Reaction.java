package com.socialapp.reaction.entity;

import com.socialapp.common.enums.ReactionType;
import com.socialapp.common.enums.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's reaction on some target entity. The target may live in
 * post-service, comment-service or reels-service; rather than wiring up a
 * separate Feign client to each of them just to resolve the owner, the
 * caller supplies {@code targetOwnerId} directly in the request body. This
 * is a deliberate simplification for this pass.
 */
@Entity
@Table(name = "reactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_reaction_target_user",
        columnNames = {"target_type", "target_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reaction {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "target_owner_id")
    private String targetOwnerId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReactionType type;

    private Instant createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
