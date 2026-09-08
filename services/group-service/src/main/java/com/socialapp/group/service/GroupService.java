package com.socialapp.group.service;

import com.socialapp.common.event.GroupEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.moderation.ProfanityFilter;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.group.dto.CreateGroupRequest;
import com.socialapp.group.entity.Group;
import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.entity.GroupPrivacy;
import com.socialapp.group.entity.MemberRole;
import com.socialapp.group.entity.MemberStatus;
import com.socialapp.group.repository.GroupMemberRepository;
import com.socialapp.group.repository.GroupRepository;
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
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Group createGroup(CreateGroupRequest request) {
        String userId = requireUserId();
        rejectIfProfane(request.name());
        rejectIfProfane(request.description());

        Group group = Group.builder()
                .name(request.name())
                .description(request.description())
                .privacy(request.privacy())
                .ownerId(userId)
                .memberCount(1)
                .build();
        group = groupRepository.save(group);

        GroupMember owner = GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .role(MemberRole.ADMIN)
                .status(MemberStatus.APPROVED)
                .joinedAt(Instant.now())
                .build();
        groupMemberRepository.save(owner);

        publish(group.getId(), userId, null, "CREATED");
        return group;
    }

    public Group getGroup(String id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));
    }

    public Page<Group> listVisibleGroups(String name, Pageable pageable) {
        // Every PUBLIC group, plus any PRIVATE group the caller already has an
        // APPROVED membership in. CurrentUserContext.getUserId() may be null here
        // (this endpoint doesn't require auth). Both query params are normalized to
        // "" rather than null — a null bind parameter used only in an equality/IS
        // NULL check defeats Postgres's JDBC type inference (it falls back to
        // bytea, which then breaks the query's LOWER(...) usage) — "" never
        // matches a real id or a real name, so the intended "no filter" /
        // "no memberships" behavior is unchanged.
        String callerId = CurrentUserContext.getUserId();
        String normalizedCallerId = callerId == null ? "" : callerId;
        String normalizedName = (name == null || name.isBlank()) ? "" : name;
        return groupRepository.findVisibleGroups(normalizedCallerId, normalizedName, pageable);
    }

    @Transactional
    public GroupMember join(String groupId) {
        String userId = requireUserId();
        Group group = getGroup(groupId);

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("Already a member (or pending) of this group");
        }

        GroupMember member;
        if (group.getPrivacy() == GroupPrivacy.PUBLIC) {
            member = GroupMember.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .joinedAt(Instant.now())
                    .build();
            groupMemberRepository.save(member);

            group.setMemberCount(group.getMemberCount() + 1);
            groupRepository.save(group);

            publish(groupId, userId, userId, "JOINED");
        } else {
            member = GroupMember.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .joinedAt(null)
                    .build();
            groupMemberRepository.save(member);

            publish(groupId, userId, group.getOwnerId(), "JOIN_REQUESTED");
        }
        return member;
    }

    @Transactional
    public GroupMember approveMember(String groupId, String userId) {
        String currentUserId = requireUserId();
        requireRole(groupId, currentUserId, MemberRole.ADMIN, MemberRole.MODERATOR);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(m -> m.getStatus() == MemberStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending membership for user " + userId));

        member.setStatus(MemberStatus.APPROVED);
        member.setJoinedAt(Instant.now());
        groupMemberRepository.save(member);

        Group group = getGroup(groupId);
        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);

        publish(groupId, currentUserId, userId, "APPROVED");
        return member;
    }

    @Transactional
    public void leave(String groupId) {
        String userId = requireUserId();
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not a member of this group"));

        boolean wasApproved = member.getStatus() == MemberStatus.APPROVED;
        groupMemberRepository.delete(member);

        if (wasApproved) {
            Group group = getGroup(groupId);
            group.setMemberCount(Math.max(0, group.getMemberCount() - 1));
            groupRepository.save(group);
        }
    }

    @Transactional
    public void removeMember(String groupId, String userId) {
        String currentUserId = requireUserId();
        requireRole(groupId, currentUserId, MemberRole.ADMIN);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this group"));

        boolean wasApproved = member.getStatus() == MemberStatus.APPROVED;
        groupMemberRepository.delete(member);

        if (wasApproved) {
            Group group = getGroup(groupId);
            group.setMemberCount(Math.max(0, group.getMemberCount() - 1));
            groupRepository.save(group);
        }

        publish(groupId, currentUserId, userId, "REMOVED");
    }

    public Page<GroupMember> listMembers(String groupId, Pageable pageable) {
        return groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.APPROVED, pageable);
    }

    public Page<Group> myGroups(Pageable pageable) {
        String userId = requireUserId();
        Page<GroupMember> memberships = groupMemberRepository.findByUserIdAndStatus(userId, MemberStatus.APPROVED, pageable);

        List<String> groupIds = memberships.getContent().stream()
                .map(GroupMember::getGroupId)
                .toList();
        Map<String, Group> groupsById = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, g -> g));
        List<Group> groups = groupIds.stream()
                .map(groupsById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(groups, pageable, memberships.getTotalElements());
    }

    private void requireRole(String groupId, String userId, MemberRole... allowedRoles) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have permission to perform this action"));
        boolean allowed = member.getStatus() == MemberStatus.APPROVED
                && java.util.Arrays.asList(allowedRoles).contains(member.getRole());
        if (!allowed) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }

    /** ADMIN-only. Guards against demoting the group's last remaining ADMIN. */
    @Transactional
    public GroupMember changeRole(String groupId, String targetUserId, MemberRole newRole) {
        String currentUserId = requireUserId();
        requireRole(groupId, currentUserId, MemberRole.ADMIN);

        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("No approved membership for user " + targetUserId));

        if (target.getRole() == MemberRole.ADMIN && newRole != MemberRole.ADMIN
                && groupMemberRepository.countByGroupIdAndRoleAndStatus(groupId, MemberRole.ADMIN, MemberStatus.APPROVED) <= 1) {
            throw new BadRequestException("Cannot demote the last admin of the group");
        }

        target.setRole(newRole);
        return groupMemberRepository.save(target);
    }

    private void publish(String groupId, String actorId, String targetUserId, String type) {
        kafkaTemplate.send(KafkaTopics.GROUP, new GroupEvent(groupId, actorId, targetUserId, type, Instant.now()));
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
    public void removeForModeration(String groupId) {
        groupRepository.findById(groupId).ifPresent(group -> {
            groupMemberRepository.deleteByGroupId(groupId);
            groupRepository.delete(group);
        });
    }

    private void rejectIfProfane(String content) {
        if (ProfanityFilter.containsProfanity(content)) {
            throw new BadRequestException("Content violates community guidelines");
        }
    }
}
