package com.socialapp.reaction.service;

import com.socialapp.common.enums.ReactionType;
import com.socialapp.common.enums.TargetType;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reaction.dto.ReactionSummaryResponse;
import com.socialapp.reaction.dto.UpsertReactionRequest;
import com.socialapp.reaction.entity.Reaction;
import com.socialapp.reaction.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Reaction upsert(UpsertReactionRequest request) {
        if (request.targetType() == null || request.targetId() == null || request.type() == null) {
            throw new BadRequestException("targetType, targetId and type are required");
        }
        String userId = CurrentUserContext.getUserId();

        Reaction reaction = reactionRepository
                .findByTargetTypeAndTargetIdAndUserId(request.targetType(), request.targetId(), userId)
                .map(existing -> {
                    existing.setType(request.type());
                    existing.setTargetOwnerId(request.targetOwnerId());
                    return existing;
                })
                .orElseGet(() -> Reaction.builder()
                        .targetType(request.targetType())
                        .targetId(request.targetId())
                        .targetOwnerId(request.targetOwnerId())
                        .userId(userId)
                        .type(request.type())
                        .build());

        Reaction saved = reactionRepository.save(reaction);

        publishEvent(saved, false);
        return saved;
    }

    public void delete(TargetType targetType, String targetId) {
        String userId = CurrentUserContext.getUserId();
        Optional<Reaction> existing = reactionRepository.findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
        if (existing.isEmpty()) {
            return;
        }
        Reaction reaction = existing.get();
        reactionRepository.delete(reaction);
        publishEvent(reaction, true);
    }

    public Optional<Reaction> getMyReaction(TargetType targetType, String targetId) {
        String userId = CurrentUserContext.getUserId();
        return reactionRepository.findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
    }

    public ReactionSummaryResponse getSummary(TargetType targetType, String targetId) {
        Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        long total = 0;
        for (ReactionRepository.TypeCount tc : reactionRepository.countByTarget(targetType, targetId)) {
            counts.put(tc.getType(), tc.getCount());
            total += tc.getCount();
        }
        return new ReactionSummaryResponse(counts, total);
    }

    private void publishEvent(Reaction reaction, boolean removed) {
        ReactionEvent event = new ReactionEvent(
                reaction.getId(),
                reaction.getTargetType().name(),
                reaction.getTargetId(),
                reaction.getTargetOwnerId(),
                reaction.getUserId(),
                reaction.getType().name(),
                removed,
                Instant.now());
        kafkaTemplate.send(KafkaTopics.REACTION, reaction.getTargetId(), event);
    }
}
