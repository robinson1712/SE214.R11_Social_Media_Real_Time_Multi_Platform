package com.socialapp.post.entity;

import com.socialapp.common.enums.Privacy;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String authorId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_media_urls", joinColumns = @jakarta.persistence.JoinColumn(name = "post_id"))
    @Column(name = "media_url")
    @Builder.Default
    private List<String> mediaUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Privacy privacy = Privacy.PUBLIC;

    // Only meaningful when privacy == CUSTOM — the explicit viewer allow-list.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_custom_audience", joinColumns = @jakarta.persistence.JoinColumn(name = "post_id"))
    @Column(name = "user_id")
    @Builder.Default
    private List<String> customAudienceUserIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_tagged_users", joinColumns = @jakarta.persistence.JoinColumn(name = "post_id"))
    @Column(name = "user_id")
    @Builder.Default
    private List<String> taggedUserIds = new ArrayList<>();

    private String groupId;

    private String pageId;

    // Non-null when this post is a share/repost of another post; the shared post
    // is fetched separately (GET /api/posts/{sharedPostId}) rather than embedded.
    private String sharedPostId;

    // Non-null when this post is a share/repost of a reel instead — mutually
    // exclusive with sharedPostId. Fetched separately from reels-service
    // (GET /api/reels/{sharedReelId}), same reasoning as sharedPostId above.
    private String sharedReelId;

    @Builder.Default
    private int commentCount = 0;

    @Builder.Default
    private int reactionCount = 0;

    // Explicit SQL-level DEFAULT — without it, Hibernate's ALTER TABLE ADD COLUMN
    // ... NOT NULL (no default) fails against a posts table that already has rows,
    // since Postgres has no way to backfill the existing rows. @Builder.Default
    // alone only affects the Java-side builder, not the generated DDL. Same
    // reasoning applies to shareCount below (see "pinned" — this bit us for real
    // once already when adding the pinned column against a populated table).
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pinned = false;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int shareCount = 0;

    private Instant createdAt;

    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
