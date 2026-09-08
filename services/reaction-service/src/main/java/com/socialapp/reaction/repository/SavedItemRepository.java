package com.socialapp.reaction.repository;

import com.socialapp.common.enums.TargetType;
import com.socialapp.reaction.entity.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, String> {

    Optional<SavedItem> findByTargetTypeAndTargetIdAndUserId(TargetType targetType, String targetId, String userId);

    void deleteByTargetTypeAndTargetIdAndUserId(TargetType targetType, String targetId, String userId);

    Page<SavedItem> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
