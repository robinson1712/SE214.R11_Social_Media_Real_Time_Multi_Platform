package com.socialapp.chat.dto;

import java.util.List;

public record CreateConversationRequest(List<String> participantIds) {
}
