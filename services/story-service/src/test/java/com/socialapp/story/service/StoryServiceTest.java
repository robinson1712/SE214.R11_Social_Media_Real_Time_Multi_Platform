package com.socialapp.story.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.StoryCreatedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.story.client.UserServiceClient;
import com.socialapp.story.document.MediaType;
import com.socialapp.story.document.Story;
import com.socialapp.story.dto.CreateStoryRequest;
import com.socialapp.story.dto.TextOverlayRequest;
import com.socialapp.story.repository.StoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StoryService. CurrentUserContext is set/cleared per test via
 * the test-support seam in common-lib (setForTests/clearForTests) rather than
 * going through a real HTTP request and HeaderAuthFilter.
 */
@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private UserServiceClient userServiceClient;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private StoryService storyService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        storyService = new StoryService(storyRepository, userServiceClient, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Story existingStory(String id, String authorId) {
        return Story.builder()
                .id(id)
                .authorId(authorId)
                .mediaUrl("http://media/1.png")
                .mediaType(MediaType.IMAGE)
                .caption("caption")
                .viewerIds(new HashSet<>())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();
    }

    @Test
    void createStory_savesWith24HourExpiryAndPublishesEvent() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateStoryRequest request = new CreateStoryRequest("http://media/1.png", MediaType.IMAGE, "hello", null);
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = storyService.createStory(request);

        assertThat(saved.getAuthorId()).isEqualTo("author-1");
        assertThat(saved.getMediaUrl()).isEqualTo("http://media/1.png");
        assertThat(saved.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(saved.getCaption()).isEqualTo("hello");
        assertThat(Duration.between(saved.getCreatedAt(), saved.getExpiresAt())).isEqualTo(Duration.ofHours(24));

        ArgumentCaptor<StoryCreatedEvent> captor = ArgumentCaptor.forClass(StoryCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.STORY_CREATED), eq("author-1"), captor.capture());
        assertThat(captor.getValue().storyId()).isEqualTo(saved.getId());
        assertThat(captor.getValue().authorId()).isEqualTo("author-1");
    }

    @Test
    void createStory_profaneCaption_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateStoryRequest request = new CreateStoryRequest("http://media/1.png", MediaType.IMAGE, "you fucking idiot", null);

        assertThatThrownBy(() -> storyService.createStory(request))
                .isInstanceOf(BadRequestException.class);

        verify(storyRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void createStory_withTextOverlays_mapsAllFieldsAndDefaultsMissingFontSize() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateStoryRequest request = new CreateStoryRequest("http://media/1.png", MediaType.IMAGE, null, List.of(
                new TextOverlayRequest("Hello!", "Pacifico", "#FFFFFF", 0.5, 0.2, 32.0),
                new TextOverlayRequest("no size", "Roboto", "#FF0000", 0.1, 0.9, null)
        ));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = storyService.createStory(request);

        assertThat(saved.getTextOverlays()).hasSize(2);
        assertThat(saved.getTextOverlays().get(0).getText()).isEqualTo("Hello!");
        assertThat(saved.getTextOverlays().get(0).getFontFamily()).isEqualTo("Pacifico");
        assertThat(saved.getTextOverlays().get(0).getColor()).isEqualTo("#FFFFFF");
        assertThat(saved.getTextOverlays().get(0).getFontSize()).isEqualTo(32.0);
        assertThat(saved.getTextOverlays().get(1).getFontSize()).isEqualTo(24.0);
    }

    @Test
    void createStory_profaneOverlayText_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateStoryRequest request = new CreateStoryRequest("http://media/1.png", MediaType.IMAGE, null, List.of(
                new TextOverlayRequest("you fucking idiot", "Roboto", "#FFFFFF", 0.5, 0.5, 24.0)
        ));

        assertThatThrownBy(() -> storyService.createStory(request))
                .isInstanceOf(BadRequestException.class);

        verify(storyRepository, never()).save(any());
    }

    @Test
    void createStory_noTextOverlays_savesEmptyList() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateStoryRequest request = new CreateStoryRequest("http://media/1.png", MediaType.IMAGE, "hello", null);
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story saved = storyService.createStory(request);

        assertThat(saved.getTextOverlays()).isEmpty();
    }

    @Test
    void getFeed_includesSelfEvenWithNoFriends() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(userServiceClient.getFriendIds("user-1")).thenReturn(List.of());
        when(storyRepository.findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        storyService.getFeed();

        ArgumentCaptor<List<String>> authorIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(storyRepository).findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(authorIdsCaptor.capture(), any(Instant.class));
        assertThat(authorIdsCaptor.getValue()).containsExactly("user-1");
    }

    @Test
    void getFeed_combinesFriendsAndSelf() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(userServiceClient.getFriendIds("user-1")).thenReturn(List.of("friend-1", "friend-2"));
        when(storyRepository.findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        storyService.getFeed();

        ArgumentCaptor<List<String>> authorIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(storyRepository).findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(authorIdsCaptor.capture(), any(Instant.class));
        assertThat(authorIdsCaptor.getValue()).containsExactlyInAnyOrder("user-1", "friend-1", "friend-2");
    }

    @Test
    void getFeed_friendClientReturnsNull_treatedAsEmpty() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(userServiceClient.getFriendIds("user-1")).thenReturn(null);
        when(storyRepository.findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        storyService.getFeed();

        ArgumentCaptor<List<String>> authorIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(storyRepository).findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(authorIdsCaptor.capture(), any(Instant.class));
        assertThat(authorIdsCaptor.getValue()).containsExactly("user-1");
    }

    @Test
    void getStoriesByAuthor_delegatesToRepository() {
        Story story = existingStory("story-1", "author-1");
        when(storyRepository.findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(eq("author-1"), any(Instant.class)))
                .thenReturn(List.of(story));

        List<Story> result = storyService.getStoriesByAuthor("author-1");

        assertThat(result).containsExactly(story);
    }

    @Test
    void markViewed_addsCurrentUserToViewerIds() {
        CurrentUserContext.setForTests("viewer-1", List.of("USER"));
        Story story = existingStory("story-1", "author-1");
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story result = storyService.markViewed("story-1");

        assertThat(result.getViewerIds()).contains("viewer-1");
        verify(storyRepository).save(story);
    }

    @Test
    void markViewed_sameViewerTwice_doesNotDuplicateInViewerIdsSet() {
        CurrentUserContext.setForTests("viewer-1", List.of("USER"));
        Story story = existingStory("story-1", "author-1");
        Set<String> viewerIds = new HashSet<>();
        viewerIds.add("viewer-1");
        story.setViewerIds(viewerIds);
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story result = storyService.markViewed("story-1");

        assertThat(result.getViewerIds()).containsExactly("viewer-1");
        verify(storyRepository).save(story);
    }

    @Test
    void markViewed_missingStory_throwsResourceNotFound() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storyService.markViewed("missing"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storyRepository, never()).save(any());
    }

    @Test
    void deleteStory_byAuthor_deletes() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Story story = existingStory("story-1", "author-1");
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));

        storyService.deleteStory("story-1");

        verify(storyRepository).delete(story);
    }

    @Test
    void deleteStory_byNonAuthor_throwsForbiddenAndNeverDeletes() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        Story story = existingStory("story-1", "author-1");
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> storyService.deleteStory("story-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(storyRepository, never()).delete(any(Story.class));
    }

    @Test
    void deleteStory_missingStory_throwsResourceNotFound() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storyService.deleteStory("missing"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storyRepository, never()).delete(any(Story.class));
    }

    @Test
    void removeForModeration_existing_deletesIt() {
        Story story = existingStory("story-1", "author-1");
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(story));

        storyService.removeForModeration("story-1");

        verify(storyRepository).delete(story);
    }

    @Test
    void removeForModeration_missing_isNoOp() {
        when(storyRepository.findById("missing")).thenReturn(Optional.empty());

        storyService.removeForModeration("missing");

        verify(storyRepository, never()).delete(any(Story.class));
    }
}
