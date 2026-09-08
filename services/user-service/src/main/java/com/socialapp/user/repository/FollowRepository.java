package com.socialapp.user.repository;

import com.socialapp.user.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, String> {
    Optional<Follow> findByFollowerIdAndFolloweeId(String followerId, String followeeId);
    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);
    Page<Follow> findByFollowerId(String followerId, Pageable pageable);
    Page<Follow> findByFolloweeId(String followeeId, Pageable pageable);
    long countByFollowerId(String followerId);
    long countByFolloweeId(String followeeId);
}
