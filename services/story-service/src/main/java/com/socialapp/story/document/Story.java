package com.socialapp.story.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document("stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    private String id;

    private String authorId;

    private String mediaUrl;

    private MediaType mediaType;

    private String caption;

    @Builder.Default
    private Set<String> viewerIds = new HashSet<>();

    @Builder.Default
    private List<TextOverlay> textOverlays = new ArrayList<>();

    private Instant createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;
}
