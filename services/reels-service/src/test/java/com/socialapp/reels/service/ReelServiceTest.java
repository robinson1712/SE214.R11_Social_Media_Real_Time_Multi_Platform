package com.socialapp.reels.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReelCreatedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reels.document.Reel;
import com.socialapp.reels.dto.CreateReelRequest;
import com.socialapp.reels.repository.ReelRepository;
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
 * Unit tests for ReelService. CurrentUserContext is set/cleared per test via
 * the test-support seam in common-lib (setForTests/clearForTests) rather than
 * going through a real HTTP request and HeaderAuthFilter.
 */
@ExtendWith(MockitoExtension.class)
class ReelServiceTest {

    @Mock
    private ReelRepository reelRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ReelService reelService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        reelService = new ReelService(reelRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Reel existingReel(String id, String authorId) {
        return Reel.builder()
                .id(id)
                .authorId(authorId)
                .videoUrl("http://media/video.mp4")
                .thumbnailUrl("http://media/thumb.png")
                .caption("caption")
                .viewCount(0)
                .commentCount(0)
                .reactionCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createReel_savesAndPublishesEvent() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateReelRequest request = new CreateReelRequest("http://media/video.mp4", "http://media/thumb.png", "hello");
        when(reelRepository.save(any(Reel.class))).thenAnswer(inv -> inv.getArgument(0));

        Reel saved = reelService.createReel(request);

        assertThat(saved.getAuthorId()).isEqualTo("author-1");
        assertThat(saved.getVideoUrl()).isEqualTo("http://media/video.mp4");
        assertThat(saved.getThumbnailUrl()).isEqualTo("http://media/thumb.png");
        assertThat(saved.getCaption()).isEqualTo("hello");

        ArgumentCaptor<ReelCreatedEvent> captor = ArgumentCaptor.forClass(ReelCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REEL_CREATED), eq("author-1"), captor.capture());
        assertThat(captor.getValue().reelId()).isEqualTo(saved.getId());
        assertThat(captor.getValue().authorId()).isEqualTo("author-1");
    }

    @Test
    void createReel_profaneCaption_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        CreateReelRequest request = new CreateReelRequest("http://media/video.mp4", "http://media/thumb.png", "you fucking idiot");

        assertThatThrownBy(() -> reelService.createReel(request))
                .isInstanceOf(BadRequestException.class);

        verify(reelRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void getReel_found_returnsIt() {
        Reel reel = existingReel("reel-1", "author-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));

        Reel found = reelService.getReel("reel-1");

        assertThat(found.getId()).isEqualTo("reel-1");
    }

    @Test
    void getReel_missing_throwsResourceNotFound() {
        when(reelRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reelService.getReel("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void viewReel_incrementsViewCountAndSaves() {
        Reel reel = existingReel("reel-1", "author-1");
        reel.setViewCount(4);
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        when(reelRepository.save(any(Reel.class))).thenAnswer(inv -> inv.getArgument(0));

        Reel result = reelService.viewReel("reel-1");

        assertThat(result.getViewCount()).isEqualTo(5);
        verify(reelRepository).save(reel);
    }

    @Test
    void viewReel_missing_throwsResourceNotFound() {
        when(reelRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reelService.viewReel("missing"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reelRepository, never()).save(any());
    }

    @Test
    void incrementShareCount_incrementsShareCountAndSaves() {
        Reel reel = existingReel("reel-1", "author-1");
        reel.setShareCount(2);
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        when(reelRepository.save(any(Reel.class))).thenAnswer(inv -> inv.getArgument(0));

        Reel result = reelService.incrementShareCount("reel-1");

        assertThat(result.getShareCount()).isEqualTo(3);
        verify(reelRepository).save(reel);
    }

    @Test
    void incrementShareCount_missing_throwsResourceNotFound() {
        when(reelRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reelService.incrementShareCount("missing"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reelRepository, never()).save(any());
    }

    // ---------- getFeed ranking (mirrors feed-service's FeedQueryService formula) ----------

    private Reel reelWithEngagement(String id, java.time.Instant createdAt, int reactionCount, int commentCount, long viewCount) {
        return Reel.builder()
                .id(id)
                .authorId("author-1")
                .videoUrl("http://media/video.mp4")
                .createdAt(createdAt)
                .reactionCount(reactionCount)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .build();
    }

    private void mockPool(List<Reel> pool) {
        when(reelRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(pool));
    }

    @Test
    void getFeed_sameAge_higherEngagementRanksFirst() {
        java.time.Instant now = java.time.Instant.now();
        Reel low = reelWithEngagement("low", now.minus(5, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        Reel high = reelWithEngagement("high", now.minus(5, java.time.temporal.ChronoUnit.HOURS), 10, 5, 100);
        mockPool(List.of(low, high));

        Page<Reel> result = reelService.getFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Reel::getId).containsExactly("high", "low");
    }

    @Test
    void getFeed_zeroEngagementForAll_fallsBackToPureRecencyNewestFirst() {
        java.time.Instant now = java.time.Instant.now();
        Reel older = reelWithEngagement("older", now.minus(10, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        Reel newer = reelWithEngagement("newer", now.minus(1, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        mockPool(List.of(older, newer));

        Page<Reel> result = reelService.getFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Reel::getId).containsExactly("newer", "older");
    }

    @Test
    void getFeed_heavyEngagementCanOutrankAnOlderPostOverANewerOne() {
        java.time.Instant now = java.time.Instant.now();
        Reel viralButOld = reelWithEngagement("viral", now.minus(6, java.time.temporal.ChronoUnit.HOURS), 500, 200, 10000);
        Reel freshNoEngagement = reelWithEngagement("fresh", now.minus(5, java.time.temporal.ChronoUnit.MINUTES), 0, 0, 0);
        mockPool(List.of(freshNoEngagement, viralButOld));

        Page<Reel> result = reelService.getFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Reel::getId).containsExactly("viral", "fresh");
    }

    @Test
    void getFeed_pagination_slicesRankedOrderNotPoolOrder() {
        java.time.Instant now = java.time.Instant.now();
        Reel p1 = reelWithEngagement("p1", now.minus(1, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        Reel p2 = reelWithEngagement("p2", now.minus(2, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        Reel p3 = reelWithEngagement("p3", now.minus(3, java.time.temporal.ChronoUnit.HOURS), 0, 0, 0);
        // Deliberately scrambled pool order relative to recency, to prove
        // pagination slices the RANKED list, not repository order.
        mockPool(List.of(p3, p1, p2));

        Page<Reel> firstPage = reelService.getFeed(PageRequest.of(0, 2));
        Page<Reel> secondPage = reelService.getFeed(PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).extracting(Reel::getId).containsExactly("p1", "p2");
        assertThat(secondPage.getContent()).extracting(Reel::getId).containsExactly("p3");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getFeed_pageBeyondAvailableResults_returnsEmptyContent() {
        mockPool(List.of(reelWithEngagement("p1", java.time.Instant.now(), 0, 0, 0)));

        Page<Reel> result = reelService.getFeed(PageRequest.of(5, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getReelsByAuthor_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Reel reel = existingReel("reel-1", "author-1");
        Page<Reel> page = new PageImpl<>(List.of(reel));
        when(reelRepository.findByAuthorIdOrderByCreatedAtDesc("author-1", pageable)).thenReturn(page);

        Page<Reel> result = reelService.getReelsByAuthor("author-1", pageable);

        assertThat(result.getContent()).containsExactly(reel);
    }

    @Test
    void deleteReel_byAuthor_deletes() {
        CurrentUserContext.setForTests("author-1", List.of("USER"));
        Reel reel = existingReel("reel-1", "author-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));

        reelService.deleteReel("reel-1");

        verify(reelRepository).delete(reel);
    }

    @Test
    void deleteReel_byNonAuthor_throwsForbiddenAndNeverDeletes() {
        CurrentUserContext.setForTests("someone-else", List.of("USER"));
        Reel reel = existingReel("reel-1", "author-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));

        assertThatThrownBy(() -> reelService.deleteReel("reel-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(reelRepository, never()).delete(any(Reel.class));
    }

    @Test
    void deleteReel_missing_throwsResourceNotFound() {
        when(reelRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reelService.deleteReel("missing"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reelRepository, never()).delete(any(Reel.class));
    }

    @Test
    void removeForModeration_existing_deletesIt() {
        Reel reel = existingReel("reel-1", "author-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));

        reelService.removeForModeration("reel-1");

        verify(reelRepository).delete(reel);
    }

    @Test
    void removeForModeration_missing_isNoOp() {
        when(reelRepository.findById("missing")).thenReturn(Optional.empty());

        reelService.removeForModeration("missing");

        verify(reelRepository, never()).delete(any(Reel.class));
    }
}
