package com.socialapp.post.dto;

import com.socialapp.common.enums.Privacy;

/** content is the optional "thoughts" comment shown above the shared post, like Facebook's share dialog. */
public record ShareRequest(String content, Privacy privacy) {
}
