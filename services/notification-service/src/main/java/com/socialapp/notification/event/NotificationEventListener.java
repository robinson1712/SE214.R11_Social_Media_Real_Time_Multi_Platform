package com.socialapp.notification.event;

import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.FollowEvent;
import com.socialapp.common.event.FriendRequestEvent;
import com.socialapp.common.event.GroupEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.MatchEvent;
import com.socialapp.common.event.MessageEvent;
import com.socialapp.common.event.PostTaggedEvent;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.notification.document.NotificationType;
import com.socialapp.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int MESSAGE_PREVIEW_MAX_LENGTH = 60;

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.FRIEND_REQUEST, groupId = "${spring.kafka.consumer.group-id}")
    public void onFriendRequest(FriendRequestEvent event) {
        if ("PENDING".equals(event.status())) {
            notificationService.createAndPush(event.targetUserId(), event.requesterId(), NotificationType.FRIEND_REQUEST,
                    null, null, "Someone sent you a friend request");
        } else if ("ACCEPTED".equals(event.status())) {
            notificationService.createAndPush(event.requesterId(), event.targetUserId(), NotificationType.FRIEND_REQUEST,
                    null, null, "Your friend request was accepted");
        }
    }

    @KafkaListener(topics = KafkaTopics.COMMENT_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event.authorId().equals(event.targetOwnerId())) {
            return;
        }
        notificationService.createAndPush(event.targetOwnerId(), event.authorId(), NotificationType.COMMENT,
                event.targetType(), event.targetId(),
                "Someone commented on your " + event.targetType().toLowerCase());
    }

    @KafkaListener(topics = KafkaTopics.REACTION, groupId = "${spring.kafka.consumer.group-id}")
    public void onReaction(ReactionEvent event) {
        if (event.removed() || event.userId().equals(event.targetOwnerId())) {
            return;
        }
        notificationService.createAndPush(event.targetOwnerId(), event.userId(), NotificationType.REACTION,
                event.targetType(), event.targetId(), "Someone reacted to your " + event.targetType().toLowerCase());
    }

    @KafkaListener(topics = KafkaTopics.GROUP, groupId = "${spring.kafka.consumer.group-id}")
    public void onGroupEvent(GroupEvent event) {
        if (event.targetUserId() == null) {
            return;
        }
        notificationService.createAndPush(event.targetUserId(), event.actorId(), NotificationType.GROUP,
                "GROUP", event.groupId(), "Group update: " + event.type());
    }

    @KafkaListener(topics = KafkaTopics.MATCH, groupId = "${spring.kafka.consumer.group-id}")
    public void onMatch(MatchEvent event) {
        notificationService.createAndPush(event.user1Id(), event.user2Id(), NotificationType.MATCH,
                null, null, "You matched with someone new!");
        notificationService.createAndPush(event.user2Id(), event.user1Id(), NotificationType.MATCH,
                null, null, "You matched with someone new!");
    }

    @KafkaListener(topics = KafkaTopics.MESSAGE, groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(MessageEvent event) {
        if (event.recipientId() == null) {
            return;
        }
        String preview = event.preview();
        String truncated = (preview != null && preview.length() > MESSAGE_PREVIEW_MAX_LENGTH)
                ? preview.substring(0, MESSAGE_PREVIEW_MAX_LENGTH) + "..."
                : preview;
        notificationService.createAndPush(event.recipientId(), event.senderId(), NotificationType.MESSAGE,
                "CONVERSATION", event.conversationId(), "New message: " + truncated);
    }

    @KafkaListener(topics = KafkaTopics.POST_TAGGED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPostTagged(PostTaggedEvent event) {
        if (event.authorId().equals(event.taggedUserId())) {
            return;
        }
        notificationService.createAndPush(event.taggedUserId(), event.authorId(), NotificationType.TAG,
                "POST", event.postId(), "Someone tagged you in a post");
    }

    /** Only published on an explicit follow — see FollowService — so there's no unfollow counterpart to filter out here. */
    @KafkaListener(topics = KafkaTopics.FOLLOW, groupId = "${spring.kafka.consumer.group-id}")
    public void onFollow(FollowEvent event) {
        notificationService.createAndPush(event.followeeId(), event.followerId(), NotificationType.FOLLOW,
                "USER", event.followerId(), "Someone started following you");
    }
}
