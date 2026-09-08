package com.socialapp.fanpage.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.PageEvent;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.fanpage.dto.AddAdminRequest;
import com.socialapp.fanpage.dto.CreateFanpageRequest;
import com.socialapp.fanpage.entity.AdminRole;
import com.socialapp.fanpage.entity.Fanpage;
import com.socialapp.fanpage.entity.PageAdmin;
import com.socialapp.fanpage.entity.PageFollower;
import com.socialapp.fanpage.repository.FanpageRepository;
import com.socialapp.fanpage.repository.PageAdminRepository;
import com.socialapp.fanpage.repository.PageFollowerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FanpageService {

    private final FanpageRepository fanpageRepository;
    private final PageFollowerRepository pageFollowerRepository;
    private final PageAdminRepository pageAdminRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Fanpage createPage(CreateFanpageRequest request) {
        String userId = requireUserId();
        rejectIfProfane(request.name());
        rejectIfProfane(request.description());

        Fanpage page = Fanpage.builder()
                .name(request.name())
                .category(request.category())
                .description(request.description())
                .ownerId(userId)
                .followerCount(0)
                .build();
        page = fanpageRepository.save(page);

        PageAdmin owner = PageAdmin.builder()
                .pageId(page.getId())
                .userId(userId)
                .role(AdminRole.OWNER)
                .addedAt(Instant.now())
                .build();
        pageAdminRepository.save(owner);

        publish(page.getId(), userId, "CREATED");
        return page;
    }

    public Fanpage getPage(String id) {
        return fanpageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fanpage not found: " + id));
    }

    public Page<Fanpage> listPages(String name, Pageable pageable) {
        if (name != null && !name.isBlank()) {
            return fanpageRepository.findByNameContainingIgnoreCase(name, pageable);
        }
        return fanpageRepository.findAll(pageable);
    }

    @Transactional
    public PageFollower follow(String pageId) {
        String userId = requireUserId();
        Fanpage page = getPage(pageId);

        if (pageFollowerRepository.existsByPageIdAndUserId(pageId, userId)) {
            throw new ConflictException("Already following this page");
        }

        PageFollower follower = PageFollower.builder()
                .pageId(pageId)
                .userId(userId)
                .createdAt(Instant.now())
                .build();
        pageFollowerRepository.save(follower);

        page.setFollowerCount(page.getFollowerCount() + 1);
        fanpageRepository.save(page);

        publish(pageId, userId, "FOLLOWED");
        return follower;
    }

    @Transactional
    public void unfollow(String pageId) {
        String userId = requireUserId();
        PageFollower follower = pageFollowerRepository.findByPageIdAndUserId(pageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not following this page"));
        pageFollowerRepository.delete(follower);

        Fanpage page = getPage(pageId);
        page.setFollowerCount(Math.max(0, page.getFollowerCount() - 1));
        fanpageRepository.save(page);
    }

    @Transactional
    public PageAdmin addOrUpdateAdmin(String pageId, AddAdminRequest request) {
        String currentUserId = requireUserId();
        requireAdminOrOwner(pageId, currentUserId);
        getPage(pageId);

        PageAdmin admin = pageAdminRepository.findByPageIdAndUserId(pageId, request.userId())
                .orElseGet(() -> PageAdmin.builder()
                        .pageId(pageId)
                        .userId(request.userId())
                        .addedAt(Instant.now())
                        .build());
        admin.setRole(request.role());
        return pageAdminRepository.save(admin);
    }

    @Transactional
    public void removeAdmin(String pageId, String userId) {
        String currentUserId = requireUserId();
        requireAdminOrOwner(pageId, currentUserId);

        PageAdmin admin = pageAdminRepository.findByPageIdAndUserId(pageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not an admin of this page"));

        if (admin.getRole() == AdminRole.OWNER && pageAdminRepository.countByPageIdAndRole(pageId, AdminRole.OWNER) <= 1) {
            throw new BadRequestException("Cannot remove the last owner of the page");
        }

        pageAdminRepository.delete(admin);
    }

    public Page<PageFollower> listFollowers(String pageId, Pageable pageable) {
        return pageFollowerRepository.findByPageId(pageId, pageable);
    }

    public Page<Fanpage> myManagedPages(Pageable pageable) {
        String userId = requireUserId();
        Page<PageAdmin> adminRows = pageAdminRepository.findByUserId(userId, pageable);

        List<String> pageIds = adminRows.getContent().stream()
                .map(PageAdmin::getPageId)
                .toList();
        Map<String, Fanpage> pagesById = fanpageRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Fanpage::getId, p -> p));
        List<Fanpage> pages = pageIds.stream()
                .map(pagesById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(pages, pageable, adminRows.getTotalElements());
    }

    public Page<Fanpage> myFollowedPages(Pageable pageable) {
        String userId = requireUserId();
        Page<PageFollower> followerRows = pageFollowerRepository.findByUserId(userId, pageable);

        List<String> pageIds = followerRows.getContent().stream()
                .map(PageFollower::getPageId)
                .toList();
        Map<String, Fanpage> pagesById = fanpageRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Fanpage::getId, p -> p));
        List<Fanpage> pages = pageIds.stream()
                .map(pagesById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(pages, pageable, followerRows.getTotalElements());
    }

    private void requireAdminOrOwner(String pageId, String userId) {
        PageAdmin admin = pageAdminRepository.findByPageIdAndUserId(pageId, userId)
                .orElseThrow(() -> new ForbiddenException("Only page admins or the owner may perform this action"));
        if (admin.getRole() != AdminRole.OWNER && admin.getRole() != AdminRole.ADMIN) {
            throw new ForbiddenException("Only page admins or the owner may perform this action");
        }
    }

    private void publish(String pageId, String actorId, String type) {
        kafkaTemplate.send(KafkaTopics.PAGE, new PageEvent(pageId, actorId, type, Instant.now()));
    }

    private String requireUserId() {
        String userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }

    /** Driven by moderation-service's ContentRemovedEvent — idempotent, a no-op if already gone. */
    @Transactional
    public void removeForModeration(String pageId) {
        fanpageRepository.findById(pageId).ifPresent(page -> {
            pageFollowerRepository.deleteByPageId(pageId);
            pageAdminRepository.deleteByPageId(pageId);
            fanpageRepository.delete(page);
        });
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }
}
