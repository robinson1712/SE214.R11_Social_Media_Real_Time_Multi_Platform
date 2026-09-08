package com.socialapp.post.service;

import com.socialapp.common.enums.Privacy;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.PostCreatedEvent;
import com.socialapp.common.event.PostTaggedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.post.client.ReelClient;
import com.socialapp.post.client.UserServiceClient;
import com.socialapp.post.dto.CreatePostRequest;
import com.socialapp.post.dto.ShareRequest;
import com.socialapp.post.dto.UpdatePostRequest;
import com.socialapp.post.entity.Post;
import com.socialapp.post.repository.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PostService. CurrentUserContext is set/cleared per test via
 * the test-support seam in common-lib (setForTests/clearForTests) rather than
 * going through a real HTTP request and HeaderAuthFilter.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private ReelClient reelClient;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private PostService postService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        postService = new PostService(postRepository, kafkaTemplate, userServiceClient, reelClient);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Post existingPost(String id, String authorId) {
        return Post.builder()
                .id(id)
                .authorId(authorId)
                .content("original content")
                .mediaUrls(List.of())
                .privacy(Privacy.PUBLIC)
                .customAudienceUserIds(List.of())
                .taggedUserIds(List.of())
                .commentCount(0)
                .reactionCount(0)
                .build();
    }

    private CreatePostRequest createRequest(String content, Privacy privacy) {
        return new CreatePostRequest(content, null, privacy, null, null, null, null);
    }

    @Test
    void createPost_withContent_savesAndPublishesEvent() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = createRequest("Hello world", Privacy.PUBLIC);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post saved = postService.createPost(request);

        assertThat(saved.getAuthorId()).isEqualTo("author-1");
        assertThat(saved.getContent()).isEqualTo("Hello world");

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.POST_CREATED), eq(saved.getId()), captor.capture());
        assertThat(captor.getValue().authorId()).isEqualTo("author-1");
        assertThat(captor.getValue().privacy()).isEqualTo("PUBLIC");
    }

    @Test
    void createPost_mediaOnlyNoContent_isAllowed() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = new CreatePostRequest(null, List.of("http://img/1.png"), null, null, null, null, null);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post saved = postService.createPost(request);

        assertThat(saved.getMediaUrls()).containsExactly("http://img/1.png");
        // privacy wasn't specified — service must default it to PUBLIC.
        assertThat(saved.getPrivacy()).isEqualTo(Privacy.PUBLIC);
    }

    @Test
    void createPost_noContentAndNoMedia_throwsBadRequest() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = new CreatePostRequest("   ", List.of(), Privacy.PUBLIC, null, null, null, null);

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_profaneContent_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = createRequest("what the fuck is this", Privacy.PUBLIC);

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void createPost_customPrivacyWithEmptyAudience_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = new CreatePostRequest("hi", null, Privacy.CUSTOM, null, null, List.of(), null);

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_customPrivacyWithAudience_savesSuccessfully() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = new CreatePostRequest("hi", null, Privacy.CUSTOM, null, null, List.of("friend-1", "friend-2"), null);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post saved = postService.createPost(request);

        assertThat(saved.getPrivacy()).isEqualTo(Privacy.CUSTOM);
        assertThat(saved.getCustomAudienceUserIds()).containsExactly("friend-1", "friend-2");
    }

    @Test
    void createPost_withTaggedUsers_publishesOneEventPerTaggedUser() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreatePostRequest request = new CreatePostRequest("hi", null, Privacy.PUBLIC, null, null, null, List.of("tagged-1", "tagged-2"));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post saved = postService.createPost(request);

        assertThat(saved.getTaggedUserIds()).containsExactly("tagged-1", "tagged-2");
        ArgumentCaptor<PostTaggedEvent> captor = ArgumentCaptor.forClass(PostTaggedEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(KafkaTopics.POST_TAGGED), any(), captor.capture());
        assertThat(captor.getAllValues()).extracting(PostTaggedEvent::taggedUserId).containsExactlyInAnyOrder("tagged-1", "tagged-2");
    }

    @Test
    void updatePost_editingInProfanity_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.updatePost("post-1", new UpdatePostRequest("you bitch", null, null, null, null)))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updatePost_settingCustomPrivacyWithoutAudience_throwsBadRequest() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.updatePost("post-1", new UpdatePostRequest(null, null, Privacy.CUSTOM, null, null)))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updatePost_addingNewTags_onlyNotifiesNewlyAddedOnes() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        post.setTaggedUserIds(List.of("already-tagged"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        postService.updatePost("post-1", new UpdatePostRequest(null, null, null, null, List.of("already-tagged", "new-tag")));

        ArgumentCaptor<PostTaggedEvent> captor = ArgumentCaptor.forClass(PostTaggedEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(KafkaTopics.POST_TAGGED), any(), captor.capture());
        assertThat(captor.getValue().taggedUserId()).isEqualTo("new-tag");
    }

    @Test
    void removeForModeration_existingPost_deletesIt() {
        Post post = existingPost("post-1", "author-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.removeForModeration("post-1");

        verify(postRepository).delete(post);
    }

    @Test
    void removeForModeration_missingPost_isNoOp() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        postService.removeForModeration("missing");

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void getPost_found_returnsIt() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        Post found = postService.getPost("post-1");

        assertThat(found.getId()).isEqualTo("post-1");
    }

    @Test
    void getPost_missing_throwsResourceNotFound() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePost_byAuthor_updatesFields() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post updated = postService.updatePost("post-1", new UpdatePostRequest("edited content", null, null, null, null));

        assertThat(updated.getContent()).isEqualTo("edited content");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatePost_byNonAuthor_throwsForbidden() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.updatePost("post-1", new UpdatePostRequest("hacked", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void deletePost_byAuthor_deletes() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.deletePost("post-1");

        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_byNonAuthor_throwsForbiddenAndNeverDeletes() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.deletePost("post-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void incrementCommentCount_existingPost_incrementsAndSaves() {
        Post post = existingPost("post-1", "author-1");
        post.setCommentCount(2);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.incrementCommentCount("post-1");

        assertThat(post.getCommentCount()).isEqualTo(3);
        verify(postRepository).save(post);
    }

    @Test
    void incrementCommentCount_missingPost_isNoOp() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        postService.incrementCommentCount("missing");

        verify(postRepository, never()).save(any());
    }

    @Test
    void applyReactionDelta_added_incrementsCount() {
        Post post = existingPost("post-1", "author-1");
        post.setReactionCount(4);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.applyReactionDelta("post-1", false);

        assertThat(post.getReactionCount()).isEqualTo(5);
    }

    @Test
    void applyReactionDelta_removed_decrementsCount() {
        Post post = existingPost("post-1", "author-1");
        post.setReactionCount(4);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.applyReactionDelta("post-1", true);

        assertThat(post.getReactionCount()).isEqualTo(3);
    }

    @Test
    void applyReactionDelta_removedAtZero_clampsAtZeroInsteadOfGoingNegative() {
        Post post = existingPost("post-1", "author-1");
        post.setReactionCount(0);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        postService.applyReactionDelta("post-1", true);

        assertThat(post.getReactionCount()).isZero();
    }

    @Test
    void pinPost_byAuthor_setsPinnedTrue() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post pinned = postService.pinPost("post-1");

        assertThat(pinned.isPinned()).isTrue();
        assertThat(pinned.getUpdatedAt()).isNotNull();
    }

    @Test
    void pinPost_byNonAuthor_throwsForbiddenAndNeverSaves() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.pinPost("post-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void unpinPost_byAuthor_setsPinnedFalse() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        post.setPinned(true);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post unpinned = postService.unpinPost("post-1");

        assertThat(unpinned.isPinned()).isFalse();
    }

    @Test
    void unpinPost_byNonAuthor_throwsForbiddenAndNeverSaves() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        Post post = existingPost("post-1", "author-1");
        post.setPinned(true);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.unpinPost("post-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void getPostsByAuthor_delegatesToRepositoryWithGivenPageable() {
        Pageable pageable = PageRequest.of(0, 20);
        Post post = existingPost("post-1", "author-1");
        Page<Post> page = new PageImpl<>(List.of(post));
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(page);

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).containsExactly(post);
    }

    // ---------- privacy enforcement ----------

    @Test
    void getPostsByAuthor_privatePost_hiddenFromNonAuthor() {
        CurrentUserContext.setForTests("stranger", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 20);
        Post privatePost = existingPost("post-1", "author-1");
        privatePost.setPrivacy(Privacy.PRIVATE);
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(privatePost)));

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getPostsByAuthor_privatePost_visibleToAuthor() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 20);
        Post privatePost = existingPost("post-1", "author-1");
        privatePost.setPrivacy(Privacy.PRIVATE);
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(privatePost)));

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).containsExactly(privatePost);
    }

    @Test
    void getPostsByAuthor_friendsOnlyPost_visibleToFriend() {
        CurrentUserContext.setForTests("friend-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 20);
        Post friendsPost = existingPost("post-1", "author-1");
        friendsPost.setPrivacy(Privacy.FRIENDS);
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(friendsPost)));
        when(userServiceClient.getFriendIds("author-1")).thenReturn(List.of("friend-1", "friend-2"));

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).containsExactly(friendsPost);
    }

    @Test
    void getPostsByAuthor_friendsOnlyPost_hiddenFromNonFriend() {
        CurrentUserContext.setForTests("stranger", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 20);
        Post friendsPost = existingPost("post-1", "author-1");
        friendsPost.setPrivacy(Privacy.FRIENDS);
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(friendsPost)));
        when(userServiceClient.getFriendIds("author-1")).thenReturn(List.of("friend-1"));

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getPostsByAuthor_customAudiencePost_visibleOnlyToListedUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        Post customPost = existingPost("post-1", "author-1");
        customPost.setPrivacy(Privacy.CUSTOM);
        customPost.setCustomAudienceUserIds(List.of("invited-1"));
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(customPost)));

        CurrentUserContext.setForTests("invited-1", List.of("USER"));
        assertThat(postService.getPostsByAuthor("author-1", pageable).getContent()).containsExactly(customPost);

        CurrentUserContext.setForTests("not-invited", List.of("USER"));
        assertThat(postService.getPostsByAuthor("author-1", pageable).getContent()).isEmpty();
    }

    @Test
    void getPostsByAuthor_publicPost_visibleToUnauthenticatedViewer() {
        Pageable pageable = PageRequest.of(0, 20);
        Post publicPost = existingPost("post-1", "author-1");
        when(postRepository.findByAuthorId("author-1", pageable)).thenReturn(new PageImpl<>(List.of(publicPost)));

        Page<Post> result = postService.getPostsByAuthor("author-1", pageable);

        assertThat(result.getContent()).containsExactly(publicPost);
    }

    // ---------- sharePost ----------

    @Test
    void sharePost_publicPost_savesShareAndIncrementsShareCountAndPublishesEvent() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));
        Post original = existingPost("post-1", "author-1");
        when(postRepository.findById("post-1")).thenReturn(Optional.of(original));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post shared = postService.sharePost("post-1", new ShareRequest("check this out", Privacy.PUBLIC));

        assertThat(shared.getAuthorId()).isEqualTo("sharer-1");
        assertThat(shared.getSharedPostId()).isEqualTo("post-1");
        assertThat(shared.getContent()).isEqualTo("check this out");
        assertThat(original.getShareCount()).isEqualTo(1);

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.POST_CREATED), eq(shared.getId()), captor.capture());
        assertThat(captor.getValue().authorId()).isEqualTo("sharer-1");
    }

    @Test
    void sharePost_privatePostByOtherUser_throwsForbiddenAndNeverSaves() {
        CurrentUserContext.setForTests("stranger", List.of("USER"));
        Post original = existingPost("post-1", "author-1");
        original.setPrivacy(Privacy.PRIVATE);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> postService.sharePost("post-1", new ShareRequest(null, Privacy.PUBLIC)))
                .isInstanceOf(ForbiddenException.class);

        verify(postRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sharePost_missingOriginal_throwsResourceNotFound() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.sharePost("missing", new ShareRequest(null, Privacy.PUBLIC)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sharePost_profaneComment_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));

        assertThatThrownBy(() -> postService.sharePost("post-1", new ShareRequest("you bitch", Privacy.PUBLIC)))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }

    // ---------- searchPublicPosts ----------

    @Test
    void searchPublicPosts_matchingQuery_delegatesToRepositoryWithPublicPrivacy() {
        Pageable pageable = PageRequest.of(0, 20);
        Post post = existingPost("post-1", "author-1");
        when(postRepository.findByContentContainingIgnoreCaseAndPrivacy("hello", Privacy.PUBLIC, pageable))
                .thenReturn(new PageImpl<>(List.of(post)));

        Page<Post> result = postService.searchPublicPosts("hello", pageable);

        assertThat(result.getContent()).containsExactly(post);
    }

    @Test
    void searchPublicPosts_blankQuery_throwsBadRequest() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> postService.searchPublicPosts("   ", pageable))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sharePost_noPrivacySpecified_defaultsToPublic() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));
        when(postRepository.findById("post-1")).thenReturn(Optional.of(existingPost("post-1", "author-1")));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post shared = postService.sharePost("post-1", new ShareRequest(null, null));

        assertThat(shared.getPrivacy()).isEqualTo(Privacy.PUBLIC);
    }

    // ---------- shareReel ----------

    @Test
    void shareReel_savesShareBumpsReelShareCountAndPublishesEvent() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post shared = postService.shareReel("reel-1", new ShareRequest("check this reel out", Privacy.PUBLIC));

        assertThat(shared.getAuthorId()).isEqualTo("sharer-1");
        assertThat(shared.getSharedReelId()).isEqualTo("reel-1");
        assertThat(shared.getContent()).isEqualTo("check this reel out");
        verify(reelClient).incrementShareCount("reel-1");

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.POST_CREATED), eq(shared.getId()), captor.capture());
        assertThat(captor.getValue().authorId()).isEqualTo("sharer-1");
    }

    @Test
    void shareReel_noPrivacySpecified_defaultsToPublic() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post shared = postService.shareReel("reel-1", new ShareRequest(null, null));

        assertThat(shared.getPrivacy()).isEqualTo(Privacy.PUBLIC);
    }

    @Test
    void shareReel_profaneComment_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));

        assertThatThrownBy(() -> postService.shareReel("reel-1", new ShareRequest("you bitch", Privacy.PUBLIC)))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
        verify(reelClient, never()).incrementShareCount(any());
    }

    @Test
    void shareReel_blankReelId_throwsBadRequest() {
        CurrentUserContext.setForTests("sharer-1", List.of("USER"));

        assertThatThrownBy(() -> postService.shareReel("  ", new ShareRequest(null, Privacy.PUBLIC)))
                .isInstanceOf(BadRequestException.class);

        verify(postRepository, never()).save(any());
    }
}
