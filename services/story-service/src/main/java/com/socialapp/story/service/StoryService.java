package com.socialapp.story.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.StoryCreatedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.story.client.UserServiceClient;
import com.socialapp.story.document.Story;
import com.socialapp.story.document.TextOverlay;
import com.socialapp.story.dto.CreateStoryRequest;
import com.socialapp.story.dto.TextOverlayRequest;
import com.socialapp.story.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserServiceClient userServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Story createStory(CreateStoryRequest request) {
        rejectIfProfane(request.caption());
        List<TextOverlay> textOverlays = mapOverlays(request.textOverlays());
        String authorId = CurrentUserContext.getUserId();
        Instant now = Instant.now();

        Story story = Story.builder()
                .id(UUID.randomUUID().toString())
                .authorId(authorId)
                .mediaUrl(request.mediaUrl())
                .mediaType(request.mediaType())
                .caption(request.caption())
                .textOverlays(textOverlays)
                .createdAt(now)
                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                .build();

        Story saved = storyRepository.save(story);

        kafkaTemplate.send(KafkaTopics.STORY_CREATED,
                saved.getAuthorId(),
                new StoryCreatedEvent(saved.getId(), saved.getAuthorId(), Instant.now()));

        return saved;
    }

    public List<Story> getFeed() {
        String userId = CurrentUserContext.getUserId();
        List<String> friendIds = userServiceClient.getFriendIds(userId);

        List<String> authorIds = new ArrayList<>(friendIds != null ? friendIds : List.of());
        authorIds.add(userId);

        return storyRepository.findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(authorIds, Instant.now());
    }

    public List<Story> getStoriesByAuthor(String authorId) {
        return storyRepository.findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(authorId, Instant.now());
    }

    public Story markViewed(String id) {
        Story story = getStoryOrThrow(id);
        story.getViewerIds().add(CurrentUserContext.getUserId());
        return storyRepository.save(story);
    }

    public void deleteStory(String id) {
        Story story = getStoryOrThrow(id);
        String userId = CurrentUserContext.getUserId();
        if (!story.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to delete this story");
        }
        storyRepository.delete(story);
    }

    private Story getStoryOrThrow(String id) {
        return storyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + id));
    }

    /** Driven by moderation-service's ContentRemovedEvent — idempotent, a no-op if already gone. */
    public void removeForModeration(String id) {
        storyRepository.findById(id).ifPresent(storyRepository::delete);
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }

    private List<TextOverlay> mapOverlays(List<TextOverlayRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        return requests.stream()
                .map(r -> {
                    rejectIfProfane(r.text());
                    return TextOverlay.builder()
                            .text(r.text())
                            .fontFamily(r.fontFamily())
                            .color(r.color())
                            .x(r.x())
                            .y(r.y())
                            .fontSize(r.fontSize() != null ? r.fontSize() : 24.0)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
