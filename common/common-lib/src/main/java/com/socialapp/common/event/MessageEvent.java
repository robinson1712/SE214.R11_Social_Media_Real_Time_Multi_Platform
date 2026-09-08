package com.socialapp.common.event;

import java.time.Instant;

public record MessageEvent(String messageId, String conversationId, String senderId, String recipientId,
                            String preview, Instant occurredAt) {
}
