package com.socialapp.fanpage.repository;

import com.socialapp.fanpage.entity.PageFollower;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageFollowerRepository extends JpaRepository<PageFollower, String> {

    boolean existsByPageIdAndUserId(String pageId, String userId);

    Optional<PageFollower> findByPageIdAndUserId(String pageId, String userId);

    Page<PageFollower> findByPageId(String pageId, Pageable pageable);

    Page<PageFollower> findByUserId(String userId, Pageable pageable);

    void deleteByPageId(String pageId);
}
