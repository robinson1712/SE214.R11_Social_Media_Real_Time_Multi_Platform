package com.socialapp.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Records that {@code userId} dismissed {@code suggestedUserId} from their
  * friend-suggestions sidebar — hidden until {@code expiresAt}. The TTL is
  * chosen at dismiss time based on how many other candidates exist (see
  * FriendSuggestionService), so a user in a small system sees suggestions
  * resurface sooner than the standard 2 weeks. */
@Entity
@Table(name = "friend_suggestion_dismissals", uniqueConstraints = @UniqueConstraint(
        name = "uk_dismissal_user_target", columnNames = {"user_id", "suggested_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendSuggestionDismissal {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "suggested_user_id", nullable = false)
    private String suggestedUserId;

    private Instant dismissedAt;

    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (dismissedAt == null) {
            dismissedAt = Instant.now();
        }
    }
}
