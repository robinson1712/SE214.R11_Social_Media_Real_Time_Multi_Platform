package com.socialapp.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Document(collection = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String conversationId;

    private String senderId;

    private String content;

    private String mediaUrl;

    /** Set when this message is a reply to a story — see {@link com.socialapp.chat.dto.ChatSendRequest}. */
    private String storyReplyId;

    private String storyReplyPreviewUrl;

    private Instant sentAt;

    @Builder.Default
    private Set<String> readBy = new HashSet<>();

    @Builder.Default
    private boolean deleted = false;
}
