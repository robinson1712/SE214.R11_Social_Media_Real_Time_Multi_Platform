package com.socialapp.dating.repository;

import com.socialapp.dating.entity.Swipe;
import com.socialapp.dating.entity.SwipeAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SwipeRepository extends JpaRepository<Swipe, String> {

    boolean existsBySwiperIdAndTargetId(String swiperId, String targetId);

    Optional<Swipe> findBySwiperIdAndTargetIdAndAction(String swiperId, String targetId, SwipeAction action);
}
