package com.socialapp.feed.dto;

import java.util.List;

public record MarkSeenRequest(List<String> postIds) {
}
