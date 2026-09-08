package com.socialapp.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private ConversationType type;

    @Builder.Default
    private List<String> participantIds = new ArrayList<>();

    private String lastMessagePreview;

    private Instant lastMessageAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
