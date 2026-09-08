package com.socialapp.reaction.entity;

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
 * A user's bookmark of some target entity (post or reel today) — the
 * "Đã lưu" (Saved) feature. Structurally identical to Reaction (same
 * target_type/target_id/target_owner_id/user_id shape, same reasoning for
 * why targetOwnerId is supplied by the caller rather than resolved via a
 * Feign call — see Reaction.java), just without a "type" payload: a save
 * is either there or it isn't. Lives alongside Reaction in this service
 * rather than as its own microservice since the access pattern is
 * identical and doesn't warrant a separate deployable.
 */
@Entity
@Table(name = "saved_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_saved_item_target_user",
        columnNames = {"target_type", "target_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedItem {

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

    private Instant createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
