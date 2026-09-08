package com.socialapp.feed.service;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.event.PostCreatedEvent;
import com.socialapp.feed.client.FanpageClient;
import com.socialapp.feed.client.GroupClient;
import com.socialapp.feed.client.UserClient;
import com.socialapp.feed.dto.GroupMemberDto;
import com.socialapp.feed.dto.PageFollowerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FeedFanoutService — pins down which users' feed:{userId}
 * ZSETs receive a postId add() call for each PostCreatedEvent shape (group
 * post, page post, personal post at each privacy level), and confirms the
 * fallback-factory-driven "empty audience" case never throws.
 */
@ExtendWith(MockitoExtension.class)
class FeedFanoutServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private GroupClient groupClient;
    @Mock
    private FanpageClient fanpageClient;
    @Mock
    private UserClient userClient;

    private FeedFanoutService feedFanoutService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        feedFanoutService = new FeedFanoutService(redisTemplate, groupClient, fanpageClient, userClient);
    }

    private static PostCreatedEvent groupEvent(String postId, String authorId, String groupId) {
        return new PostCreatedEvent(postId, authorId, groupId, null, "PUBLIC", Instant.now());
    }

    private static PostCreatedEvent pageEvent(String postId, String authorId, String pageId) {
        return new PostCreatedEvent(postId, authorId, null, pageId, "PUBLIC", Instant.now());
    }

    private static PostCreatedEvent personalEvent(String postId, String authorId, String privacy) {
        return new PostCreatedEvent(postId, authorId, null, null, privacy, Instant.now());
    }

    private static ApiResponse<PageResponse<GroupMemberDto>> membersResponse(List<String> userIds) {
        List<GroupMemberDto> members = userIds.stream().map(GroupMemberDto::new).toList();
        PageResponse<GroupMemberDto> page = new PageResponse<>(members, 0, members.size(), members.size(), 1, true);
        return ApiResponse.success(page);
    }

    private static ApiResponse<PageResponse<PageFollowerDto>> followersResponse(List<String> userIds) {
        List<PageFollowerDto> followers = userIds.stream().map(PageFollowerDto::new).toList();
        PageResponse<PageFollowerDto> page = new PageResponse<>(followers, 0, followers.size(), followers.size(), 1, true);
        return ApiResponse.success(page);
    }

    // ---------- group posts ----------

    @Test
    void fanout_groupPost_pushesToEveryReturnedMember() {
        PostCreatedEvent event = groupEvent("post1", "author1", "group1");
        when(groupClient.getMembers(eq("group1"), anyInt()))
                .thenReturn(membersResponse(List.of("member1", "member2", "member3")));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:member1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:member2"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:member3"), eq("post1"), anyDouble());
        verifyNoInteractions(fanpageClient, userClient);
    }

    @Test
    void fanout_groupPost_authorIncludedAsMember_isNotSpecialCased() {
        // author1 is one of the returned members — should receive exactly the
        // same single add() as everyone else, not double-pushed.
        PostCreatedEvent event = groupEvent("post1", "author1", "group1");
        when(groupClient.getMembers(eq("group1"), anyInt()))
                .thenReturn(membersResponse(List.of("author1", "member2")));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:member2"), eq("post1"), anyDouble());
    }

    @Test
    void fanout_groupPost_emptyMemberList_fansOutToNobodyAndDoesNotThrow() {
        PostCreatedEvent event = groupEvent("post1", "author1", "group1");
        when(groupClient.getMembers(eq("group1"), anyInt())).thenReturn(membersResponse(Collections.emptyList()));

        feedFanoutService.fanout(event);

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void fanout_groupPost_nullApiResponse_treatedAsEmptyAudience() {
        // Simulates the fallback factory returning a bare null / degraded response.
        PostCreatedEvent event = groupEvent("post1", "author1", "group1");
        when(groupClient.getMembers(eq("group1"), anyInt())).thenReturn(null);

        feedFanoutService.fanout(event);

        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    // ---------- page posts ----------

    @Test
    void fanout_pagePost_pushesToEveryFollowerAndAlwaysToAuthor() {
        PostCreatedEvent event = pageEvent("post1", "author1", "page1");
        when(fanpageClient.getFollowers(eq("page1"), anyInt()))
                .thenReturn(followersResponse(List.of("follower1", "follower2")));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:follower1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:follower2"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verifyNoInteractions(groupClient, userClient);
    }

    @Test
    void fanout_pagePost_authorNotAFollower_stillReceivesPost() {
        PostCreatedEvent event = pageEvent("post1", "author1", "page1");
        when(fanpageClient.getFollowers(eq("page1"), anyInt()))
                .thenReturn(followersResponse(List.of("follower1")));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:follower1"), eq("post1"), anyDouble());
    }

    @Test
    void fanout_pagePost_emptyFollowerList_stillPushesToAuthorOnly() {
        PostCreatedEvent event = pageEvent("post1", "author1", "page1");
        when(fanpageClient.getFollowers(eq("page1"), anyInt())).thenReturn(followersResponse(Collections.emptyList()));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations, never()).add(eq("feed:follower1"), any(), anyDouble());
    }

    // ---------- personal posts ----------

    @Test
    void fanout_personalPost_public_pushesToAuthorAndAllFriends() {
        PostCreatedEvent event = personalEvent("post1", "author1", "PUBLIC");
        when(userClient.getFriendIds("author1")).thenReturn(List.of("friend1", "friend2"));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:friend1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:friend2"), eq("post1"), anyDouble());
        verifyNoInteractions(groupClient, fanpageClient);
    }

    @Test
    void fanout_personalPost_friends_pushesToAuthorAndAllFriends() {
        PostCreatedEvent event = personalEvent("post1", "author1", "FRIENDS");
        when(userClient.getFriendIds("author1")).thenReturn(List.of("friend1"));

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(eq("feed:friend1"), eq("post1"), anyDouble());
    }

    @Test
    void fanout_personalPost_private_pushesOnlyToAuthorAndNeverCallsFriendIds() {
        PostCreatedEvent event = personalEvent("post1", "author1", "PRIVATE");

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations, never()).add(eq("feed:friend1"), any(), anyDouble());
        verify(userClient, never()).getFriendIds(any());
        verifyNoInteractions(groupClient, fanpageClient);
        // exactly one add() call total — nobody else's feed touched.
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void fanout_personalPost_public_emptyFriendList_pushesOnlyToAuthorButStillCallsClient() {
        PostCreatedEvent event = personalEvent("post1", "author1", "PUBLIC");
        when(userClient.getFriendIds("author1")).thenReturn(Collections.emptyList());

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void fanout_personalPost_public_nullFriendListFromFallback_doesNotThrow() {
        PostCreatedEvent event = personalEvent("post1", "author1", "PUBLIC");
        when(userClient.getFriendIds("author1")).thenReturn(null);

        feedFanoutService.fanout(event);

        verify(zSetOperations).add(eq("feed:author1"), eq("post1"), anyDouble());
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }
}
