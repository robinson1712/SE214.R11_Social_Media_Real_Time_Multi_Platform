package com.socialapp.chat.dto;

/** storyReplyId/storyReplyPreviewUrl are set when this message is a reply to
  * someone's story (Facebook-style) — chat UI renders that as a distinct
  * "replied to your story" bubble with a thumbnail instead of a plain message. */
public record ChatSendRequest(
        String conversationId,
        String content,
        String mediaUrl,
        String storyReplyId,
        String storyReplyPreviewUrl
) {
}
