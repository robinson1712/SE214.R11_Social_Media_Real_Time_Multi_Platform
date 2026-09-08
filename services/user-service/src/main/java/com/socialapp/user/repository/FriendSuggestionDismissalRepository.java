package com.socialapp.user.repository;

import com.socialapp.user.entity.FriendSuggestionDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FriendSuggestionDismissalRepository extends JpaRepository<FriendSuggestionDismissal, String> {

    Optional<FriendSuggestionDismissal> findByUserIdAndSuggestedUserId(String userId, String suggestedUserId);

    @Query("select d.suggestedUserId from FriendSuggestionDismissal d "
            + "where d.userId = :userId and d.expiresAt > :now")
    List<String> findActiveDismissedIds(@Param("userId") String userId, @Param("now") Instant now);
}
