package com.socialapp.reels.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("reels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reel {

    @Id
    private String id;

    private String authorId;

    private String videoUrl;

    private String thumbnailUrl;

    private String caption;

    @Builder.Default
    private long viewCount = 0;

    @Builder.Default
    private int commentCount = 0;

    @Builder.Default
    private int reactionCount = 0;

    @Builder.Default
    private int shareCount = 0;

    private Instant createdAt;
}
