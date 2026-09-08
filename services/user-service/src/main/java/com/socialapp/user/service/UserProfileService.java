package com.socialapp.user.service;

import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.user.dto.UpdateProfileRequest;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileResponse getProfile(String id) {
        return UserProfileResponse.from(findOrThrow(id));
    }

    public UserProfileResponse getMe(String currentUserId) {
        requireAuth(currentUserId);
        return UserProfileResponse.from(findOrThrow(currentUserId));
    }

    @Transactional
    public UserProfileResponse updateMe(String currentUserId, UpdateProfileRequest request) {
        requireAuth(currentUserId);
        UserProfile profile = findOrThrow(currentUserId);
        if (request.fullName() != null) {
            profile.setFullName(request.fullName());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.dob() != null) {
            profile.setDob(request.dob());
        }
        if (request.gender() != null) {
            profile.setGender(request.gender());
        }
        if (request.location() != null) {
            profile.setLocation(request.location());
        }
        if (request.workplace() != null) {
            profile.setWorkplace(request.workplace());
        }
        if (request.readReceiptsEnabled() != null) {
            profile.setReadReceiptsEnabled(request.readReceiptsEnabled());
        }
        profile.setUpdatedAt(Instant.now());
        return UserProfileResponse.from(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse updateAvatar(String currentUserId, String mediaUrl) {
        requireAuth(currentUserId);
        UserProfile profile = findOrThrow(currentUserId);
        profile.setAvatarUrl(mediaUrl);
        profile.setUpdatedAt(Instant.now());
        return UserProfileResponse.from(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse updateCover(String currentUserId, String mediaUrl) {
        requireAuth(currentUserId);
        UserProfile profile = findOrThrow(currentUserId);
        profile.setCoverUrl(mediaUrl);
        profile.setUpdatedAt(Instant.now());
        return UserProfileResponse.from(userProfileRepository.save(profile));
    }

    public Page<UserProfileResponse> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Search query must not be blank");
        }
        return userProfileRepository.findByFullNameContainingIgnoreCase(query, pageable)
                .map(UserProfileResponse::from);
    }

    private UserProfile findOrThrow(String id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found: " + id));
    }

    private void requireAuth(String currentUserId) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
    }
}
