package com.socialapp.chat.dto;

import java.util.List;

public record PresenceBatchRequest(List<String> userIds) {
}
