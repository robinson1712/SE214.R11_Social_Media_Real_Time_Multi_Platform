package com.socialapp.post.service;

import com.socialapp.common.enums.Privacy;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.PostCreatedEvent;
import com.socialapp.common.event.PostTaggedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.post.client.ReelClient;
import com.socialapp.post.client.UserServiceClient;
import com.socialapp.post.dto.CreatePostRequest;
import com.socialapp.post.dto.ShareRequest;
import com.socialapp.post.dto.UpdatePostRequest;
import com.socialapp.post.entity.Post;
import com.socialapp.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserServiceClient userServiceClient;
    private final ReelClient reelClient;

    public Post createPost(CreatePostRequest request) {
        boolean hasContent = request.content() != null && !request.content().isBlank();
        boolean hasMedia = request.mediaUrls() != null && !request.mediaUrls().isEmpty();
        if (!hasContent && !hasMedia) {
            throw new BadRequestException("A post requires at least content or media");
        }
        rejectIfProfane(request.content());

        Privacy privacy = request.privacy() != null ? request.privacy() : Privacy.PUBLIC;
        validatePrivacyAudience(privacy, request.customAudienceUserIds());

        String authorId = CurrentUserContext.getUserId();
        Post post = Post.builder()
                .authorId(authorId)
                .content(request.content())
                .mediaUrls(request.mediaUrls() != null ? request.mediaUrls() : Collections.emptyList())
                .privacy(privacy)
                .customAudienceUserIds(request.customAudienceUserIds() != null ? request.customAudienceUserIds() : Collections.emptyList())
                .taggedUserIds(request.taggedUserIds() != null ? request.taggedUserIds() : Collections.emptyList())
                .groupId(request.groupId())
                .pageId(request.pageId())
                .build();

        Post saved = postRepository.save(post);

        PostCreatedEvent event = new PostCreatedEvent(
                saved.getId(), saved.getAuthorId(), saved.getGroupId(), saved.getPageId(),
                saved.getPrivacy().name(), Instant.now());
        kafkaTemplate.send(KafkaTopics.POST_CREATED, saved.getId(), event);

        if (request.taggedUserIds() != null) {
            request.taggedUserIds().forEach(taggedUserId -> publishTagged(saved.getId(), authorId, taggedUserId));
        }

        return saved;
    }

    /**
     * Raw, unenforced fetch — used internally (update/delete/pin ownership checks,
     * sharePost's source lookup) and by the {@code GET /api/posts/{id}} endpoint,
     * which is also called service-to-service via Feign (e.g. comment-service
     * resolving a post's owner). Those internal calls carry no viewer identity
     * (X-User-Id is only set by api-gateway, not propagated across Feign calls),
     * so enforcing privacy here would incorrectly block them. Visibility is
     * instead enforced on the human-browsing list endpoints below
     * (getPostsByAuthor/ByGroup/ByPage) and in sharePost, which are never
     * Feign-called by another service.
     */
    public Post getPost(String id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
    }

    public Post updatePost(String id, UpdatePostRequest request) {
        Post post = getPost(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!post.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("Only the author can update this post");
        }
        if (request.content() != null) {
            rejectIfProfane(request.content());
            post.setContent(request.content());
        }
        if (request.mediaUrls() != null) {
            post.setMediaUrls(request.mediaUrls());
        }
        if (request.privacy() != null) {
            post.setPrivacy(request.privacy());
        }
        if (request.customAudienceUserIds() != null) {
            post.setCustomAudienceUserIds(request.customAudienceUserIds());
        }
        validatePrivacyAudience(post.getPrivacy(), post.getCustomAudienceUserIds());

        Set<String> newlyTagged = Collections.emptySet();
        if (request.taggedUserIds() != null) {
            newlyTagged = new HashSet<>(request.taggedUserIds());
            newlyTagged.removeAll(post.getTaggedUserIds());
            post.setTaggedUserIds(request.taggedUserIds());
        }

        post.setUpdatedAt(Instant.now());
        Post saved = postRepository.save(post);

        newlyTagged.forEach(taggedUserId -> publishTagged(saved.getId(), saved.getAuthorId(), taggedUserId));

        return saved;
    }

    public void deletePost(String id) {
        Post post = getPost(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!post.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("Only the author can delete this post");
        }
        postRepository.delete(post);
    }

    public Page<Post> getPostsByAuthor(String authorId, Pageable pageable) {
        return filterVisible(postRepository.findByAuthorId(authorId, pageable), CurrentUserContext.getUserId());
    }

    public Post pinPost(String id) {
        return setPinned(id, true);
    }

    public Post unpinPost(String id) {
        return setPinned(id, false);
    }

    private Post setPinned(String id, boolean pinned) {
        Post post = getPost(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!post.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("Only the author can pin this post");
        }
        post.setPinned(pinned);
        post.setUpdatedAt(Instant.now());
        return postRepository.save(post);
    }

    public Page<Post> getPostsByGroup(String groupId, Pageable pageable) {
        return filterVisible(postRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable), CurrentUserContext.getUserId());
    }

    public Page<Post> getPostsByPage(String pageId, Pageable pageable) {
        return filterVisible(postRepository.findByPageIdOrderByCreatedAtDesc(pageId, pageable), CurrentUserContext.getUserId());
    }

    public List<Post> getPostsByIds(List<String> ids) {
        return postRepository.findByIdIn(ids);
    }

    /**
     * Global content search — deliberately PUBLIC-only. A viewer-aware search
     * (FRIENDS/CUSTOM posts included when the searcher may see them) would need
     * the same privacy-check machinery as filterVisible, but this endpoint is
     * also meant to be called by search-service via Feign (no viewer identity
     * propagated), so scoping to PUBLIC keeps the result correct for every caller
     * without needing that plumbing yet.
     */
    public Page<Post> searchPublicPosts(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Search query must not be blank");
        }
        return postRepository.findByContentContainingIgnoreCaseAndPrivacy(query, Privacy.PUBLIC, pageable);
    }

    /** Share/repost — creates a new post that references the original; original.shareCount is incremented. */
    public Post sharePost(String id, ShareRequest request) {
        Post original = getPost(id);
        String currentUserId = CurrentUserContext.getUserId();
        if (!canView(original, currentUserId)) {
            throw new ForbiddenException("You do not have permission to share this post");
        }
        if (request.content() != null) {
            rejectIfProfane(request.content());
        }
        Privacy privacy = request.privacy() != null ? request.privacy() : Privacy.PUBLIC;
        // Sharing doesn't collect a custom audience list (ShareRequest has no such
        // field) — CUSTOM would always fail the non-empty-audience check below,
        // which is an intentional simplification: use a regular post for that.
        validatePrivacyAudience(privacy, Collections.emptyList());

        Post share = Post.builder()
                .authorId(currentUserId)
                .content(request.content())
                .privacy(privacy)
                .sharedPostId(id)
                .build();
        Post saved = postRepository.save(share);

        original.setShareCount(original.getShareCount() + 1);
        postRepository.save(original);

        PostCreatedEvent event = new PostCreatedEvent(
                saved.getId(), saved.getAuthorId(), saved.getGroupId(), saved.getPageId(),
                saved.getPrivacy().name(), Instant.now());
        kafkaTemplate.send(KafkaTopics.POST_CREATED, saved.getId(), event);

        return saved;
    }

    /** Share/repost a reel as a new post — reels have no privacy concept (see
     * reels-service's ReelController: every reel is public), so unlike
     * sharePost there's no canView gate here. */
    public Post shareReel(String reelId, ShareRequest request) {
        if (reelId == null || reelId.isBlank()) {
            throw new BadRequestException("reelId is required");
        }
        String currentUserId = CurrentUserContext.getUserId();
        if (request.content() != null) {
            rejectIfProfane(request.content());
        }
        Privacy privacy = request.privacy() != null ? request.privacy() : Privacy.PUBLIC;
        validatePrivacyAudience(privacy, Collections.emptyList());

        Post share = Post.builder()
                .authorId(currentUserId)
                .content(request.content())
                .privacy(privacy)
                .sharedReelId(reelId)
                .build();
        Post saved = postRepository.save(share);

        reelClient.incrementShareCount(reelId);

        PostCreatedEvent event = new PostCreatedEvent(
                saved.getId(), saved.getAuthorId(), saved.getGroupId(), saved.getPageId(),
                saved.getPrivacy().name(), Instant.now());
        kafkaTemplate.send(KafkaTopics.POST_CREATED, saved.getId(), event);

        return saved;
    }

    public void incrementCommentCount(String postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setCommentCount(post.getCommentCount() + 1);
            postRepository.save(post);
        });
    }

    public void applyReactionDelta(String postId, boolean removed) {
        postRepository.findById(postId).ifPresent(post -> {
            int newCount = removed ? post.getReactionCount() - 1 : post.getReactionCount() + 1;
            post.setReactionCount(Math.max(newCount, 0));
            postRepository.save(post);
        });
    }

    /** Driven by moderation-service's ContentRemovedEvent — idempotent, a no-op if already gone. */
    public void removeForModeration(String postId) {
        postRepository.findById(postId).ifPresent(postRepository::delete);
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }

    private void validatePrivacyAudience(Privacy privacy, List<String> customAudienceUserIds) {
        if (privacy == Privacy.CUSTOM && (customAudienceUserIds == null || customAudienceUserIds.isEmpty())) {
            throw new BadRequestException("CUSTOM privacy requires at least one user in customAudienceUserIds");
        }
    }

    private void publishTagged(String postId, String authorId, String taggedUserId) {
        PostTaggedEvent event = new PostTaggedEvent(postId, authorId, taggedUserId, Instant.now());
        kafkaTemplate.send(KafkaTopics.POST_TAGGED, taggedUserId, event);
    }

    /** Author always sees their own post regardless of privacy. */
    private boolean canView(Post post, String viewerId) {
        if (viewerId != null && post.getAuthorId().equals(viewerId)) {
            return true;
        }
        return switch (post.getPrivacy()) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case FRIENDS -> viewerId != null && userServiceClient.getFriendIds(post.getAuthorId()).contains(viewerId);
            case CUSTOM -> viewerId != null && post.getCustomAudienceUserIds().contains(viewerId);
        };
    }

    /**
     * Filters a page down to what the viewer may see. totalElements is kept from
     * the original (pre-filter) page rather than recomputed — same trade-off the
     * codebase already accepts elsewhere (myGroups/myManagedPages): a page may
     * come back with fewer items than its reported size, but pagination stays
     * simple and doesn't require a second privacy-aware count query.
     */
    private Page<Post> filterVisible(Page<Post> page, String viewerId) {
        List<Post> visible = page.getContent().stream()
                .filter(post -> canView(post, viewerId))
                .toList();
        return new PageImpl<>(visible, page.getPageable(), page.getTotalElements());
    }
}
