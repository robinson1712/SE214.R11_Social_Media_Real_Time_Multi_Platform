package com.socialapp.notification.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String recipientId;

    private String actorId;

    private NotificationType type;

    private String targetType;

    private String targetId;

    private String message;

    @Builder.Default
    private boolean read = false;

    /** MongoDB TTL index — notifications are auto-deleted 8 weeks after creation. */
    @Indexed(expireAfterSeconds = 8 * 7 * 24 * 60 * 60)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
