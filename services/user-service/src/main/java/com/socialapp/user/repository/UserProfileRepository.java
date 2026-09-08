package com.socialapp.user.repository;

import com.socialapp.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    List<UserProfile> findByIdIn(List<String> ids);

    Page<UserProfile> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
}
