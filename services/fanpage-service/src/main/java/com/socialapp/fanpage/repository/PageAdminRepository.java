package com.socialapp.fanpage.repository;

import com.socialapp.fanpage.entity.AdminRole;
import com.socialapp.fanpage.entity.PageAdmin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageAdminRepository extends JpaRepository<PageAdmin, String> {

    Optional<PageAdmin> findByPageIdAndUserId(String pageId, String userId);

    long countByPageIdAndRole(String pageId, AdminRole role);

    Page<PageAdmin> findByUserId(String userId, Pageable pageable);

    void deleteByPageId(String pageId);
}
