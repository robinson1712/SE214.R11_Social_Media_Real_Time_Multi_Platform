package com.socialapp.notification.service;

import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.notification.document.Notification;
import com.socialapp.notification.document.NotificationType;
import com.socialapp.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationService — persistence + WebSocket push on
 * creation, and the REST-facing read/mark-read/unread-count operations,
 * including the ownership check on markRead. The repository and
 * SimpMessagingTemplate are mocked.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private SimpMessagingTemplate messagingTemplate;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        notificationService = new NotificationService(notificationRepository, messagingTemplate);
    }

    @Test
    void createAndPush_savesNotificationAndPushesToRecipientQueue() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.createAndPush(
                "bob", "alice", NotificationType.FRIEND_REQUEST, null, null, "Someone sent you a friend request");

        assertThat(result.getRecipientId()).isEqualTo("bob");
        assertThat(result.getActorId()).isEqualTo("alice");
        assertThat(result.getType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(result.isRead()).isFalse();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/notifications"), captor.capture());
        assertThat(captor.getValue()).isSameAs(result);
    }

    @Test
    void getMyNotifications_delegatesToRepositoryForCurrentUser() {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        Page<Notification> expected = new PageImpl<>(List.of(mock(Notification.class)));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("alice", pageable)).thenReturn(expected);

        Page<Notification> result = notificationService.getMyNotifications("alice", pageable);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void markRead_owner_marksReadAndSaves() {
        Notification notification = Notification.builder()
                .id("notif-1").recipientId("alice").actorId("bob")
                .type(NotificationType.MESSAGE).read(false).createdAt(Instant.now()).build();
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.markRead("notif-1", "alice");

        assertThat(result.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_notOwner_throwsForbiddenAndNeverSaves() {
        Notification notification = Notification.builder()
                .id("notif-1").recipientId("alice").actorId("bob")
                .type(NotificationType.MESSAGE).read(false).createdAt(Instant.now()).build();
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead("notif-1", "eve"))
                .isInstanceOf(ForbiddenException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_notFound_throwsResourceNotFound() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead("missing", "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllRead_marksEveryUnreadNotificationForUserAndSavesAll() {
        Notification n1 = Notification.builder().id("n1").recipientId("alice").read(false).createdAt(Instant.now()).build();
        Notification n2 = Notification.builder().id("n2").recipientId("alice").read(false).createdAt(Instant.now()).build();
        when(notificationRepository.findByRecipientIdAndReadFalse("alice")).thenReturn(List.of(n1, n2));

        notificationService.markAllRead("alice");

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    @Test
    void unreadCount_delegatesToRepositoryForCurrentUser() {
        when(notificationRepository.countByRecipientIdAndReadFalse("alice")).thenReturn(5L);

        long count = notificationService.unreadCount("alice");

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void delete_owner_deletes() {
        Notification notification = Notification.builder()
                .id("notif-1").recipientId("alice").actorId("bob")
                .type(NotificationType.MESSAGE).read(false).createdAt(Instant.now()).build();
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        notificationService.delete("notif-1", "alice");

        verify(notificationRepository).delete(notification);
    }

    @Test
    void delete_notOwner_throwsForbiddenAndNeverDeletes() {
        Notification notification = Notification.builder()
                .id("notif-1").recipientId("alice").actorId("bob")
                .type(NotificationType.MESSAGE).read(false).createdAt(Instant.now()).build();
        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.delete("notif-1", "eve"))
                .isInstanceOf(ForbiddenException.class);

        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete("missing", "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
