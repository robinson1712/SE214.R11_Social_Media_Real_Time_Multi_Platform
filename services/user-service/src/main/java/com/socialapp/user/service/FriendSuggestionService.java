package com.socialapp.user.service;

import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.FriendSuggestionResponse;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.entity.Block;
import com.socialapp.user.entity.Friendship;
import com.socialapp.user.entity.FriendSuggestionDismissal;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.BlockRepository;
import com.socialapp.user.repository.FriendSuggestionDismissalRepository;
import com.socialapp.user.repository.FriendshipRepository;
import com.socialapp.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facebook-style "People you may know" for the right sidebar — ranked by
 * mutual-friend count, falling back to any other eligible user when the
 * system doesn't have enough people to rank meaningfully. Dismissing a
 * suggestion hides it for a while; how long adapts to how small the overall
 * candidate pool is, so a near-empty system doesn't leave the sidebar
 * permanently blank after a few dismissals.
 */
@Service
@RequiredArgsConstructor
public class FriendSuggestionService {

    /** Normal dismiss cooldown once the system has a healthy pool of other candidates. */
    private static final Duration LONG_DISMISS_TTL = Duration.ofDays(14);
    /** Shorter cooldown when candidates are scarce, so the sidebar doesn't go empty for long. */
    private static final Duration SHORT_DISMISS_TTL = Duration.ofHours(36);
    /** At or below this many eligible candidates (ignoring dismissals), the pool counts as "small". */
    private static final int SMALL_POOL_THRESHOLD = 15;

    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final UserProfileRepository userProfileRepository;
    private final FriendSuggestionDismissalRepository dismissalRepository;

    public List<FriendSuggestionResponse> suggestions(String currentUserId, int limit) {
        requireAuth(currentUserId);

        Set<String> friendIds = friendIdsOf(currentUserId);
        Set<String> excluded = excludedIds(currentUserId, friendIds);

        List<UserProfile> eligibleIgnoringDismissal = userProfileRepository.findAll().stream()
                .filter(p -> !excluded.contains(p.getId()))
                .collect(Collectors.toList());

        Set<String> dismissedIds = new HashSet<>(
                dismissalRepository.findActiveDismissedIds(currentUserId, Instant.now()));
        List<UserProfile> candidates = eligibleIgnoringDismissal.stream()
                .filter(p -> !dismissedIds.contains(p.getId()))
                .collect(Collectors.toList());
        // A small system that's had everyone dismissed once shouldn't show an
        // empty sidebar forever between TTL expiries — surface them anyway.
        if (candidates.isEmpty() && !eligibleIgnoringDismissal.isEmpty()) {
            candidates = eligibleIgnoringDismissal;
        }

        Map<String, Integer> mutualCounts = mutualFriendCounts(currentUserId, friendIds, excluded);

        candidates.sort((a, b) -> {
            int diff = mutualCounts.getOrDefault(b.getId(), 0) - mutualCounts.getOrDefault(a.getId(), 0);
            if (diff != 0) return diff;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return candidates.stream()
                .limit(limit)
                .map(p -> new FriendSuggestionResponse(
                        UserProfileResponse.from(p), mutualCounts.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void dismiss(String currentUserId, String targetId) {
        requireAuth(currentUserId);

        Set<String> friendIds = friendIdsOf(currentUserId);
        Set<String> excluded = excludedIds(currentUserId, friendIds);
        long poolSize = userProfileRepository.findAll().stream()
                .filter(p -> !excluded.contains(p.getId()))
                .count();
        Duration ttl = poolSize <= SMALL_POOL_THRESHOLD ? SHORT_DISMISS_TTL : LONG_DISMISS_TTL;

        Instant now = Instant.now();
        FriendSuggestionDismissal dismissal = dismissalRepository
                .findByUserIdAndSuggestedUserId(currentUserId, targetId)
                .orElseGet(() -> FriendSuggestionDismissal.builder()
                        .userId(currentUserId)
                        .suggestedUserId(targetId)
                        .build());
        dismissal.setDismissedAt(now);
        dismissal.setExpiresAt(now.plus(ttl));
        dismissalRepository.save(dismissal);
    }

    private Set<String> friendIdsOf(String userId) {
        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> otherParty(f, userId))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> excludedIds(String currentUserId, Set<String> friendIds) {
        Set<String> excluded = new HashSet<>(friendIds);
        excluded.add(currentUserId);
        for (Friendship f : friendshipRepository.findPendingInvolving(currentUserId)) {
            excluded.add(otherParty(f, currentUserId));
        }
        for (Block b : blockRepository.findByBlockerIdOrBlockedId(currentUserId, currentUserId)) {
            excluded.add(b.getBlockerId().equals(currentUserId) ? b.getBlockedId() : b.getBlockerId());
        }
        return excluded;
    }

    /** How many of the current user's friends is each candidate also friends with. */
    private Map<String, Integer> mutualFriendCounts(String currentUserId, Set<String> friendIds, Set<String> excluded) {
        Map<String, Integer> counts = new HashMap<>();
        for (String friendId : friendIds) {
            for (Friendship f : friendshipRepository.findAcceptedFriendships(friendId)) {
                String other = otherParty(f, friendId);
                if (other.equals(currentUserId) || excluded.contains(other)) continue;
                counts.merge(other, 1, Integer::sum);
            }
        }
        return counts;
    }

    private String otherParty(Friendship f, String userId) {
        return f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
    }

    private void requireAuth(String currentUserId) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
    }
}
