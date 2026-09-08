package com.socialapp.post.dto;

import com.socialapp.common.enums.Privacy;

import java.util.List;

public record UpdatePostRequest(
        String content,
        List<String> mediaUrls,
        Privacy privacy,
        /** Only used when privacy == CUSTOM. Null means "leave unchanged", same as the other fields. */
        List<String> customAudienceUserIds,
        List<String> taggedUserIds
) {
}
