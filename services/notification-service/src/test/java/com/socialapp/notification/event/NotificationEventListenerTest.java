package com.socialapp.notification.event;

import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.FollowEvent;
import com.socialapp.common.event.FriendRequestEvent;
import com.socialapp.common.event.GroupEvent;
import com.socialapp.common.event.MatchEvent;
import com.socialapp.common.event.MessageEvent;
import com.socialapp.common.event.PostTaggedEvent;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.notification.document.Notification;
import com.socialapp.notification.document.NotificationType;
import com.socialapp.notification.repository.NotificationRepository;
import com.socialapp.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationEventListener's six @KafkaListener methods,
 * invoked directly (no real Kafka broker). Wraps a real NotificationService
 * around a mocked repository + SimpMessagingTemplate so both the "was a
 * notification created" outcome and its exact recipient/actor/type/message
 * are verified, alongside every documented skip condition.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        NotificationService notificationService = new NotificationService(notificationRepository, messagingTemplate);
        listener = new NotificationEventListener(notificationService);
    }

    private void stubSaveEcho() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- FriendRequestEvent ----

    @Test
    void onFriendRequest_pending_notifiesTargetUserFromRequester() {
        stubSaveEcho();
        FriendRequestEvent event = new FriendRequestEvent("alice", "bob", "PENDING", Instant.now());

        listener.onFriendRequest(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(captor.getValue().getMessage()).isEqualTo("Someone sent you a friend request");
    }

    @Test
    void onFriendRequest_accepted_notifiesOriginalRequesterFromTarget() {
        stubSaveEcho();
        FriendRequestEvent event = new FriendRequestEvent("alice", "bob", "ACCEPTED", Instant.now());

        listener.onFriendRequest(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("alice");
        assertThat(captor.getValue().getActorId()).isEqualTo("bob");
        assertThat(captor.getValue().getMessage()).isEqualTo("Your friend request was accepted");
    }

    @Test
    void onFriendRequest_declined_noNotificationCreated() {
        FriendRequestEvent event = new FriendRequestEvent("alice", "bob", "DECLINED", Instant.now());

        listener.onFriendRequest(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- CommentCreatedEvent ----

    @Test
    void onCommentCreated_authorNotOwner_notifiesPostOwner() {
        stubSaveEcho();
        CommentCreatedEvent event = new CommentCreatedEvent("c1", "POST", "post-1", "alice", "bob", null, Instant.now());

        listener.onCommentCreated(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(captor.getValue().getTargetType()).isEqualTo("POST");
        assertThat(captor.getValue().getTargetId()).isEqualTo("post-1");
    }

    @Test
    void onCommentCreated_authorIsOwner_selfCommentSkipped() {
        CommentCreatedEvent event = new CommentCreatedEvent("c1", "POST", "post-1", "alice", "alice", null, Instant.now());

        listener.onCommentCreated(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- ReactionEvent ----

    @Test
    void onReaction_notRemovedAndNotSelf_notifiesTargetOwner() {
        stubSaveEcho();
        ReactionEvent event = new ReactionEvent("r1", "POST", "post-1", "bob", "alice", "LIKE", false, Instant.now());

        listener.onReaction(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.REACTION);
        assertThat(captor.getValue().getMessage()).isEqualTo("Someone reacted to your post");
    }

    @Test
    void onReaction_removed_skipped() {
        ReactionEvent event = new ReactionEvent("r1", "POST", "post-1", "bob", "alice", "LIKE", true, Instant.now());

        listener.onReaction(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void onReaction_selfReaction_skipped() {
        ReactionEvent event = new ReactionEvent("r1", "POST", "post-1", "alice", "alice", "LIKE", false, Instant.now());

        listener.onReaction(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- GroupEvent ----

    @Test
    void onGroupEvent_hasTargetUser_notifiesThatUser() {
        stubSaveEcho();
        GroupEvent event = new GroupEvent("group-1", "alice", "bob", "MEMBER_ADDED", Instant.now());

        listener.onGroupEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.GROUP);
        assertThat(captor.getValue().getTargetType()).isEqualTo("GROUP");
        assertThat(captor.getValue().getTargetId()).isEqualTo("group-1");
        assertThat(captor.getValue().getMessage()).isEqualTo("Group update: MEMBER_ADDED");
    }

    @Test
    void onGroupEvent_nullTargetUser_skipped() {
        GroupEvent event = new GroupEvent("group-1", "alice", null, "MEMBER_ADDED", Instant.now());

        listener.onGroupEvent(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- MatchEvent ----

    @Test
    void onMatch_createsOneNotificationPerMatchedUser() {
        stubSaveEcho();
        MatchEvent event = new MatchEvent("match-1", "alice", "bob", Instant.now());

        listener.onMatch(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0).getRecipientId()).isEqualTo("alice");
        assertThat(captor.getAllValues().get(0).getActorId()).isEqualTo("bob");
        assertThat(captor.getAllValues().get(1).getRecipientId()).isEqualTo("bob");
        assertThat(captor.getAllValues().get(1).getActorId()).isEqualTo("alice");
        assertThat(captor.getAllValues()).allMatch(n -> n.getType() == NotificationType.MATCH);
    }

    // ---- MessageEvent ----

    @Test
    void onMessage_hasRecipient_notifiesRecipientWithPreview() {
        stubSaveEcho();
        MessageEvent event = new MessageEvent("m1", "conv-1", "alice", "bob", "hey there!", Instant.now());

        listener.onMessage(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MESSAGE);
        assertThat(captor.getValue().getTargetType()).isEqualTo("CONVERSATION");
        assertThat(captor.getValue().getTargetId()).isEqualTo("conv-1");
        assertThat(captor.getValue().getMessage()).isEqualTo("New message: hey there!");
    }

    @Test
    void onMessage_previewLongerThan60Chars_isTruncatedWithEllipsis() {
        stubSaveEcho();
        String longPreview = "x".repeat(80);
        MessageEvent event = new MessageEvent("m1", "conv-1", "alice", "bob", longPreview, Instant.now());

        listener.onMessage(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        String expectedTruncated = "x".repeat(60) + "...";
        assertThat(captor.getValue().getMessage()).isEqualTo("New message: " + expectedTruncated);
    }

    @Test
    void onMessage_nullRecipient_skipped() {
        MessageEvent event = new MessageEvent("m1", "conv-1", "alice", null, "hey", Instant.now());

        listener.onMessage(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- PostTaggedEvent ----

    @Test
    void onPostTagged_authorNotTaggedUser_notifiesTaggedUser() {
        stubSaveEcho();
        PostTaggedEvent event = new PostTaggedEvent("post-1", "alice", "bob", Instant.now());

        listener.onPostTagged(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.TAG);
        assertThat(captor.getValue().getTargetType()).isEqualTo("POST");
        assertThat(captor.getValue().getTargetId()).isEqualTo("post-1");
    }

    @Test
    void onPostTagged_authorTaggedSelf_skipped() {
        PostTaggedEvent event = new PostTaggedEvent("post-1", "alice", "alice", Instant.now());

        listener.onPostTagged(event);

        verify(notificationRepository, never()).save(any());
    }

    // ---- FollowEvent ----

    @Test
    void onFollow_notifiesFollowee() {
        stubSaveEcho();
        FollowEvent event = new FollowEvent("alice", "bob", Instant.now());

        listener.onFollow(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("bob");
        assertThat(captor.getValue().getActorId()).isEqualTo("alice");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FOLLOW);
        assertThat(captor.getValue().getTargetType()).isEqualTo("USER");
        assertThat(captor.getValue().getTargetId()).isEqualTo("alice");
    }
}
