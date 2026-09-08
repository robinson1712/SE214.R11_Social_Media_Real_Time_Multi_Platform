package com.socialapp.feed.service;

import com.socialapp.common.event.PostCreatedEvent;
import com.socialapp.feed.client.FanpageClient;
import com.socialapp.feed.client.GroupClient;
import com.socialapp.feed.client.UserClient;
import com.socialapp.feed.dto.GroupMemberDto;
import com.socialapp.feed.dto.PageFollowerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Each Feign client here (UserClient, GroupClient, FanpageClient) is wired
 * with a Resilience4j circuit breaker + fallback (see application.yml and the
 * *FallbackFactory classes in the client package) — a downstream failure never
 * throws here, it just resolves to an empty audience, so this class doesn't
 * need its own try/catch around these calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFanoutService {

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final int MAX_FEED_SIZE = 500;
    private static final int MEMBER_PAGE_SIZE = 1000;

    private final StringRedisTemplate redisTemplate;
    private final GroupClient groupClient;
    private final FanpageClient fanpageClient;
    private final UserClient userClient;

    public void fanout(PostCreatedEvent event) {
        if (event.groupId() != null) {
            fanoutToGroup(event);
        } else if (event.pageId() != null) {
            fanoutToPage(event);
        } else {
            fanoutPersonal(event);
        }
    }

    private void fanoutToGroup(PostCreatedEvent event) {
        List<String> memberIds = fetchGroupMemberIds(event.groupId());
        for (String memberId : memberIds) {
            pushToFeed(memberId, event.postId());
        }
    }

    private void fanoutToPage(PostCreatedEvent event) {
        List<String> followerIds = fetchPageFollowerIds(event.pageId());
        for (String followerId : followerIds) {
            pushToFeed(followerId, event.postId());
        }
        pushToFeed(event.authorId(), event.postId());
    }

    private void fanoutPersonal(PostCreatedEvent event) {
        pushToFeed(event.authorId(), event.postId());

        if ("PUBLIC".equals(event.privacy()) || "FRIENDS".equals(event.privacy())) {
            List<String> friendIds = fetchFriendIds(event.authorId());
            for (String friendId : friendIds) {
                pushToFeed(friendId, event.postId());
            }
        }
    }

    private void pushToFeed(String userId, String postId) {
        if (userId == null || postId == null) {
            return;
        }
        String key = FEED_KEY_PREFIX + userId;
        double score = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(key, postId, score);
        redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_FEED_SIZE + 1));
    }

    private List<String> fetchGroupMemberIds(String groupId) {
        var response = groupClient.getMembers(groupId, MEMBER_PAGE_SIZE);
        if (response == null || response.data() == null || response.data().content() == null) {
            return Collections.emptyList();
        }
        return response.data().content().stream().map(GroupMemberDto::userId).toList();
    }

    private List<String> fetchPageFollowerIds(String pageId) {
        var response = fanpageClient.getFollowers(pageId, MEMBER_PAGE_SIZE);
        if (response == null || response.data() == null || response.data().content() == null) {
            return Collections.emptyList();
        }
        return response.data().content().stream().map(PageFollowerDto::userId).toList();
    }

    private List<String> fetchFriendIds(String userId) {
        List<String> friendIds = userClient.getFriendIds(userId);
        return friendIds == null ? Collections.emptyList() : friendIds;
    }
}
