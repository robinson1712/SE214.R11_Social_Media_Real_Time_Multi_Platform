package com.socialapp.reaction.service;

import com.socialapp.common.enums.TargetType;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reaction.dto.SaveItemRequest;
import com.socialapp.reaction.entity.SavedItem;
import com.socialapp.reaction.repository.SavedItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavedItemService {

    private final SavedItemRepository savedItemRepository;

    public SavedItem save(SaveItemRequest request) {
        if (request.targetType() == null || request.targetId() == null) {
            throw new BadRequestException("targetType and targetId are required");
        }
        String userId = CurrentUserContext.getUserId();

        return savedItemRepository.findByTargetTypeAndTargetIdAndUserId(request.targetType(), request.targetId(), userId)
                .orElseGet(() -> {
                    try {
                        return savedItemRepository.save(SavedItem.builder()
                                .targetType(request.targetType())
                                .targetId(request.targetId())
                                .targetOwnerId(request.targetOwnerId())
                                .userId(userId)
                                .build());
                    } catch (DataIntegrityViolationException e) {
                        // Two concurrent taps on the same item raced past the
                        // findBy check above — the unique constraint caught
                        // it, so the row is already there; just return it.
                        return savedItemRepository
                                .findByTargetTypeAndTargetIdAndUserId(request.targetType(), request.targetId(), userId)
                                .orElseThrow(() -> e);
                    }
                });
    }

    public void unsave(TargetType targetType, String targetId) {
        String userId = CurrentUserContext.getUserId();
        savedItemRepository.deleteByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
    }

    public Optional<SavedItem> getMine(TargetType targetType, String targetId) {
        String userId = CurrentUserContext.getUserId();
        return savedItemRepository.findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
    }

    public Page<SavedItem> listMine(Pageable pageable) {
        String userId = CurrentUserContext.getUserId();
        return savedItemRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
