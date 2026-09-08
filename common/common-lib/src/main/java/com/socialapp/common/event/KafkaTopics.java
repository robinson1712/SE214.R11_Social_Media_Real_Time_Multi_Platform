package com.socialapp.common.event;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String USER_REGISTERED = "user-registered-events";
    public static final String FRIEND_REQUEST = "friend-request-events";
    public static final String POST_CREATED = "post-created-events";
    public static final String COMMENT_CREATED = "comment-created-events";
    public static final String REACTION = "reaction-events";
    public static final String STORY_CREATED = "story-created-events";
    public static final String REEL_CREATED = "reel-created-events";
    public static final String GROUP = "group-events";
    public static final String PAGE = "page-events";
    public static final String MATCH = "match-events";
    public static final String MESSAGE = "message-events";
    public static final String CONTENT_REMOVED = "content-removed-events";
    public static final String POST_TAGGED = "post-tagged-events";
    public static final String FOLLOW = "follow-events";
}
