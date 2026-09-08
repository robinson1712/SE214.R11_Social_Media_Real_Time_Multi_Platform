package com.socialapp.reels.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReelCreatedEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reels.document.Reel;
import com.socialapp.reels.dto.CreateReelRequest;
import com.socialapp.reels.repository.ReelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * getFeed re-ranks like feed-service's FeedQueryService: pull a bounded,
 * recency-ordered candidate pool, score each by engagement with time decay,
 * then paginate the ranked list in memory. Writes (view/comment/reaction
 * counters) stay untouched by this — ranking only happens at read time.
 */
@Service
@RequiredArgsConstructor
public class ReelService {

    private static final int CANDIDATE_POOL_SIZE = 200;
    private static final double REACTION_WEIGHT = 1.0;
    private static final double COMMENT_WEIGHT = 2.0;
    private static final double VIEW_WEIGHT = 0.05;
    /** Hacker News uses 1.8; a social feed wants engagement to decay a bit slower. */
    private static final double GRAVITY = 1.5;

    private final ReelRepository reelRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Reel createReel(CreateReelRequest request) {
        rejectIfProfane(request.caption());
        String authorId = CurrentUserContext.getUserId();

        Reel reel = Reel.builder()
                .id(UUID.randomUUID().toString())
                .authorId(authorId)
                .videoUrl(request.videoUrl())
                .thumbnailUrl(request.thumbnailUrl())
                .caption(request.caption())
                .createdAt(Instant.now())
                .build();

        Reel saved = reelRepository.save(reel);

        kafkaTemplate.send(KafkaTopics.REEL_CREATED,
                saved.getAuthorId(),
                new ReelCreatedEvent(saved.getId(), saved.getAuthorId(), Instant.now()));

        return saved;
    }

    public Reel getReel(String id) {
        return getReelOrThrow(id);
    }

    public Reel viewReel(String id) {
        Reel reel = getReelOrThrow(id);
        reel.setViewCount(reel.getViewCount() + 1);
        return reelRepository.save(reel);
    }

    /** Called by post-service (via Feign) when someone shares this reel as a
     * new post — see PostService.shareReel. */
    public Reel incrementShareCount(String id) {
        Reel reel = getReelOrThrow(id);
        reel.setShareCount(reel.getShareCount() + 1);
        return reelRepository.save(reel);
    }

    public Page<Reel> getFeed(Pageable pageable) {
        Pageable poolPageable = PageRequest.of(0, CANDIDATE_POOL_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Reel> pool = reelRepository.findAllByOrderByCreatedAtDesc(poolPageable).getContent();

        List<Reel> ranked = pool.stream()
                .sorted(Comparator.comparingDouble(this::rankScore).reversed())
                .toList();

        int from = Math.min((int) pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        return new PageImpl<>(ranked.subList(from, to), pageable, ranked.size());
    }

    private double rankScore(Reel reel) {
        double ageHours = reel.getCreatedAt() == null
                ? 0
                : Duration.between(reel.getCreatedAt(), Instant.now()).toMinutes() / 60.0;

        // Pure time decay — with zero engagement this alone reduces to a plain
        // recency ordering, matching the previous behavior for un-engaged reels.
        double recencyScore = 1.0 / Math.pow(ageHours + 2, GRAVITY);

        double engagement = REACTION_WEIGHT * reel.getReactionCount()
                + COMMENT_WEIGHT * reel.getCommentCount()
                + VIEW_WEIGHT * reel.getViewCount();
        double engagementBoost = Math.log(1 + engagement);

        return recencyScore * (1 + engagementBoost);
    }

    public Page<Reel> getReelsByAuthor(String authorId, Pageable pageable) {
        return reelRepository.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable);
    }

    public void deleteReel(String id) {
        Reel reel = getReelOrThrow(id);
        String userId = CurrentUserContext.getUserId();
        if (!reel.getAuthorId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to delete this reel");
        }
        reelRepository.delete(reel);
    }

    private Reel getReelOrThrow(String id) {
        return reelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reel not found: " + id));
    }

    /** Driven by moderation-service's ContentRemovedEvent — idempotent, a no-op if already gone. */
    public void removeForModeration(String id) {
        reelRepository.findById(id).ifPresent(reelRepository::delete);
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }
}
