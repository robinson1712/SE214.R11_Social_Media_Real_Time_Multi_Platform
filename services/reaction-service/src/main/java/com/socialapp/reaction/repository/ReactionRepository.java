package com.socialapp.reaction.repository;

import com.socialapp.common.enums.ReactionType;
import com.socialapp.common.enums.TargetType;
import com.socialapp.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, String> {

    Optional<Reaction> findByTargetTypeAndTargetIdAndUserId(TargetType targetType, String targetId, String userId);

    void deleteByTargetTypeAndTargetIdAndUserId(TargetType targetType, String targetId, String userId);

    @Query("select r.type as type, count(r) as count from Reaction r " +
            "where r.targetType = :targetType and r.targetId = :targetId group by r.type")
    List<TypeCount> countByTarget(@Param("targetType") TargetType targetType, @Param("targetId") String targetId);

    interface TypeCount {
        ReactionType getType();
        Long getCount();
    }
}
