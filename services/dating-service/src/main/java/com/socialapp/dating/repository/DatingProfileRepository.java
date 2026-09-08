package com.socialapp.dating.repository;

import com.socialapp.dating.entity.DatingProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DatingProfileRepository extends JpaRepository<DatingProfile, String> {

    /**
     * A bounded, recency-ordered pool of not-yet-swiped active profiles. Gender
     * mutual-fit and age/interest compatibility scoring both happen in Java
     * (DatingService) rather than here — mixing two different enum types
     * (Gender vs GenderPreference) into one JPQL predicate isn't worth the
     * readability cost for a pool this size. Cap the pool with {@code pageable}
     * (e.g. PageRequest.of(0, 300)); this is a plain LIMIT, not a real page —
     * DatingService re-paginates the scored, sorted result itself.
     */
    @Query("SELECT p FROM DatingProfile p WHERE p.active = true AND p.id <> :selfId " +
            "AND p.id NOT IN (SELECT s.targetId FROM Swipe s WHERE s.swiperId = :selfId)")
    List<DatingProfile> findCandidatePool(@Param("selfId") String selfId, Pageable pageable);
}
