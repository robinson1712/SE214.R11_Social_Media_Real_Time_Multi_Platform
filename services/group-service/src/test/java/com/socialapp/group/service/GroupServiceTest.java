package com.socialapp.group.service;

import com.socialapp.common.event.GroupEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.group.dto.CreateGroupRequest;
import com.socialapp.group.entity.Group;
import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.entity.GroupPrivacy;
import com.socialapp.group.entity.MemberRole;
import com.socialapp.group.entity.MemberStatus;
import com.socialapp.group.repository.GroupMemberRepository;
import com.socialapp.group.repository.GroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GroupService. Repositories and Kafka are mocked so these
 * exercise only the service's own decisions (membership state transitions,
 * admin gating, member-count bookkeeping, and the "" vs null normalization
 * that findVisibleGroups relies on).
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        groupService = new GroupService(groupRepository, groupMemberRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Group existingGroup(String id, String ownerId, GroupPrivacy privacy, int memberCount) {
        return Group.builder()
                .id(id)
                .name("Test Group")
                .description("desc")
                .privacy(privacy)
                .ownerId(ownerId)
                .memberCount(memberCount)
                .build();
    }

    private GroupMember existingMember(String groupId, String userId, MemberRole role, MemberStatus status) {
        return GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(status)
                .build();
    }

    // ---------- createGroup ----------

    @Test
    void createGroup_savesGroupAndOwnerMembershipAndPublishesEvent() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateGroupRequest request = new CreateGroupRequest("My Group", "desc", GroupPrivacy.PUBLIC);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        Group saved = groupService.createGroup(request);

        assertThat(saved.getOwnerId()).isEqualTo("owner-1");
        assertThat(saved.getMemberCount()).isEqualTo(1);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        GroupMember owner = memberCaptor.getValue();
        assertThat(owner.getGroupId()).isEqualTo(saved.getId());
        assertThat(owner.getUserId()).isEqualTo("owner-1");
        assertThat(owner.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(owner.getStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(owner.getJoinedAt()).isNotNull();

        ArgumentCaptor<GroupEvent> eventCaptor = ArgumentCaptor.forClass(GroupEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.GROUP), eventCaptor.capture());
        assertThat(eventCaptor.getValue().groupId()).isEqualTo(saved.getId());
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("owner-1");
        assertThat(eventCaptor.getValue().targetUserId()).isNull();
        assertThat(eventCaptor.getValue().type()).isEqualTo("CREATED");
    }

    @Test
    void createGroup_noAuthenticatedCaller_throwsUnauthorized() {
        CreateGroupRequest request = new CreateGroupRequest("My Group", "desc", GroupPrivacy.PUBLIC);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(groupRepository, never()).save(any());
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void createGroup_profaneName_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateGroupRequest request = new CreateGroupRequest("fucking idiots group", "desc", GroupPrivacy.PUBLIC);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BadRequestException.class);

        verify(groupRepository, never()).save(any());
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void createGroup_profaneDescription_throwsBadRequestAndNeverSaves() {
        CurrentUserContext.setForTests("owner-1", List.of("USER"));
        CreateGroupRequest request = new CreateGroupRequest("My Group", "you fucking idiot", GroupPrivacy.PUBLIC);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(BadRequestException.class);

        verify(groupRepository, never()).save(any());
        verify(groupMemberRepository, never()).save(any());
    }

    // ---------- getGroup ----------

    @Test
    void getGroup_found_returnsIt() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 1)));

        Group found = groupService.getGroup("group-1");

        assertThat(found.getId()).isEqualTo("group-1");
    }

    @Test
    void getGroup_missing_throwsResourceNotFound() {
        when(groupRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroup("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- listVisibleGroups ----------

    @Test
    void listVisibleGroups_authenticatedCallerWithNameFilter_normalizesNeitherToEmptyString() {
        CurrentUserContext.setForTests("caller-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findVisibleGroups(anyString(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        groupService.listVisibleGroups("some name", pageable);

        ArgumentCaptor<String> callerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(groupRepository).findVisibleGroups(callerCaptor.capture(), nameCaptor.capture(), eq(pageable));
        assertThat(callerCaptor.getValue()).isEqualTo("caller-1");
        assertThat(nameCaptor.getValue()).isEqualTo("some name");
    }

    @Test
    void listVisibleGroups_authenticatedCallerNoNameFilter_normalizesNullNameToEmptyString() {
        CurrentUserContext.setForTests("caller-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findVisibleGroups(anyString(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        groupService.listVisibleGroups(null, pageable);

        ArgumentCaptor<String> callerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(groupRepository).findVisibleGroups(callerCaptor.capture(), nameCaptor.capture(), eq(pageable));
        assertThat(callerCaptor.getValue()).isEqualTo("caller-1");
        assertThat(nameCaptor.getValue()).isEqualTo("");
    }

    @Test
    void listVisibleGroups_blankNameFilter_normalizesToEmptyString() {
        CurrentUserContext.setForTests("caller-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findVisibleGroups(anyString(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        groupService.listVisibleGroups("   ", pageable);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(groupRepository).findVisibleGroups(anyString(), nameCaptor.capture(), eq(pageable));
        assertThat(nameCaptor.getValue()).isEqualTo("");
    }

    @Test
    void listVisibleGroups_noAuthenticatedCaller_normalizesNullCallerIdToEmptyString() {
        // No CurrentUserContext.setForTests call — simulates an unauthenticated request.
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findVisibleGroups(anyString(), anyString(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        groupService.listVisibleGroups("name", pageable);

        ArgumentCaptor<String> callerCaptor = ArgumentCaptor.forClass(String.class);
        verify(groupRepository).findVisibleGroups(callerCaptor.capture(), anyString(), eq(pageable));
        assertThat(callerCaptor.getValue()).isEqualTo("");
    }

    // ---------- join ----------

    @Test
    void join_publicGroup_isImmediatelyApprovedAndIncrementsMemberCount() {
        CurrentUserContext.setForTests("joiner-1", List.of("USER"));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId("group-1", "joiner-1")).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember member = groupService.join("group-1");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getJoinedAt()).isNotNull();

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(6);

        ArgumentCaptor<GroupEvent> eventCaptor = ArgumentCaptor.forClass(GroupEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.GROUP), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("JOINED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("joiner-1");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo("joiner-1");
    }

    @Test
    void join_privateGroup_createsPendingMembershipAndNotifiesOwnerWithoutChangingMemberCount() {
        CurrentUserContext.setForTests("joiner-1", List.of("USER"));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PRIVATE, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId("group-1", "joiner-1")).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember member = groupService.join("group-1");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(member.getJoinedAt()).isNull();

        verify(groupRepository, never()).save(any(Group.class));

        ArgumentCaptor<GroupEvent> eventCaptor = ArgumentCaptor.forClass(GroupEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.GROUP), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("JOIN_REQUESTED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("joiner-1");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo("owner-1");
    }

    @Test
    void join_alreadyMember_throwsConflictAndNeverPersistsOrPublishes() {
        CurrentUserContext.setForTests("joiner-1", List.of("USER"));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId("group-1", "joiner-1")).thenReturn(true);

        assertThatThrownBy(() -> groupService.join("group-1"))
                .isInstanceOf(ConflictException.class);

        verify(groupMemberRepository, never()).save(any());
        verify(groupRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    // ---------- approveMember ----------

    @Test
    void approveMember_byApprovedAdmin_approvesTargetAndIncrementsMemberCount() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember pendingTarget = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.PENDING);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(pendingTarget));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PRIVATE, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember approved = groupService.approveMember("group-1", "target-1");

        assertThat(approved.getStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(approved.getJoinedAt()).isNotNull();

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(6);

        ArgumentCaptor<GroupEvent> eventCaptor = ArgumentCaptor.forClass(GroupEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.GROUP), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("APPROVED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("admin-1");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo("target-1");
    }

    @Test
    void approveMember_callerNotAMember_throwsForbidden() {
        CurrentUserContext.setForTests("stranger-1", List.of("USER"));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "stranger-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.approveMember("group-1", "target-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void approveMember_callerIsPlainMember_throwsForbidden() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        GroupMember plainMember = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1")).thenReturn(Optional.of(plainMember));

        assertThatThrownBy(() -> groupService.approveMember("group-1", "target-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void approveMember_callerIsAdminButNotYetApproved_throwsForbidden() {
        CurrentUserContext.setForTests("pending-admin-1", List.of("USER"));
        GroupMember pendingAdmin = existingMember("group-1", "pending-admin-1", MemberRole.ADMIN, MemberStatus.PENDING);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "pending-admin-1")).thenReturn(Optional.of(pendingAdmin));

        assertThatThrownBy(() -> groupService.approveMember("group-1", "target-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void approveMember_noPendingMembershipForTarget_throwsResourceNotFound() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.approveMember("group-1", "target-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void approveMember_targetAlreadyApproved_throwsResourceNotFound() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember alreadyApprovedTarget = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(alreadyApprovedTarget));

        assertThatThrownBy(() -> groupService.approveMember("group-1", "target-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approveMember_byApprovedModerator_isAllowed() {
        CurrentUserContext.setForTests("mod-1", List.of("USER"));
        GroupMember moderatorMembership = existingMember("group-1", "mod-1", MemberRole.MODERATOR, MemberStatus.APPROVED);
        GroupMember pendingTarget = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.PENDING);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "mod-1")).thenReturn(Optional.of(moderatorMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(pendingTarget));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PRIVATE, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember approved = groupService.approveMember("group-1", "target-1");

        assertThat(approved.getStatus()).isEqualTo(MemberStatus.APPROVED);
    }

    // ---------- removeMember: MODERATOR is not sufficient ----------

    @Test
    void removeMember_byModerator_throwsForbidden() {
        CurrentUserContext.setForTests("mod-1", List.of("USER"));
        GroupMember moderatorMembership = existingMember("group-1", "mod-1", MemberRole.MODERATOR, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "mod-1")).thenReturn(Optional.of(moderatorMembership));

        assertThatThrownBy(() -> groupService.removeMember("group-1", "target-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(groupMemberRepository, never()).delete(any());
    }

    // ---------- changeRole ----------

    @Test
    void changeRole_byAdmin_promotesMemberToModerator() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember target = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(target));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember result = groupService.changeRole("group-1", "target-1", MemberRole.MODERATOR);

        assertThat(result.getRole()).isEqualTo(MemberRole.MODERATOR);
    }

    @Test
    void changeRole_demotingLastAdmin_throwsBadRequest() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.countByGroupIdAndRoleAndStatus("group-1", MemberRole.ADMIN, MemberStatus.APPROVED)).thenReturn(1L);

        assertThatThrownBy(() -> groupService.changeRole("group-1", "admin-1", MemberRole.MEMBER))
                .isInstanceOf(com.socialapp.common.exception.BadRequestException.class);

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void changeRole_demotingOneOfSeveralAdmins_isAllowed() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember secondAdmin = existingMember("group-1", "admin-2", MemberRole.ADMIN, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-2")).thenReturn(Optional.of(secondAdmin));
        when(groupMemberRepository.countByGroupIdAndRoleAndStatus("group-1", MemberRole.ADMIN, MemberStatus.APPROVED)).thenReturn(2L);
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember result = groupService.changeRole("group-1", "admin-2", MemberRole.MEMBER);

        assertThat(result.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void changeRole_byModerator_throwsForbidden() {
        CurrentUserContext.setForTests("mod-1", List.of("USER"));
        GroupMember moderatorMembership = existingMember("group-1", "mod-1", MemberRole.MODERATOR, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "mod-1")).thenReturn(Optional.of(moderatorMembership));

        assertThatThrownBy(() -> groupService.changeRole("group-1", "target-1", MemberRole.MODERATOR))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void changeRole_targetNotApprovedMember_throwsResourceNotFound() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.changeRole("group-1", "target-1", MemberRole.MODERATOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- leave ----------

    @Test
    void leave_approvedMember_decrementsMemberCount() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        GroupMember membership = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1")).thenReturn(Optional.of(membership));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        groupService.leave("group-1");

        verify(groupMemberRepository).delete(membership);
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(4);
    }

    @Test
    void leave_approvedMemberAtZeroCount_clampsAtZero() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        GroupMember membership = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1")).thenReturn(Optional.of(membership));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 0);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        groupService.leave("group-1");

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isZero();
    }

    @Test
    void leave_pendingMember_doesNotChangeMemberCount() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        GroupMember membership = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.PENDING);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1")).thenReturn(Optional.of(membership));

        groupService.leave("group-1");

        verify(groupMemberRepository).delete(membership);
        verify(groupRepository, never()).findById(anyString());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void leave_notAMember_throwsResourceNotFound() {
        CurrentUserContext.setForTests("stranger-1", List.of("USER"));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "stranger-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.leave("group-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(groupMemberRepository, never()).delete(any());
    }

    // ---------- removeMember ----------

    @Test
    void removeMember_byAdmin_removesApprovedMemberAndDecrementsCountAndPublishes() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember targetMembership = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(targetMembership));
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        groupService.removeMember("group-1", "target-1");

        verify(groupMemberRepository).delete(targetMembership);
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(4);

        ArgumentCaptor<GroupEvent> eventCaptor = ArgumentCaptor.forClass(GroupEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.GROUP), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("REMOVED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("admin-1");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo("target-1");
    }

    @Test
    void removeMember_pendingTarget_doesNotChangeMemberCount() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        GroupMember targetMembership = existingMember("group-1", "target-1", MemberRole.MEMBER, MemberStatus.PENDING);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.of(targetMembership));

        groupService.removeMember("group-1", "target-1");

        verify(groupMemberRepository).delete(targetMembership);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void removeMember_byNonAdmin_throwsForbiddenAndNeverRemoves() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        GroupMember plainMember = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1")).thenReturn(Optional.of(plainMember));

        assertThatThrownBy(() -> groupService.removeMember("group-1", "target-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(groupMemberRepository, never()).delete(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void removeMember_targetNotAMember_throwsResourceNotFound() {
        CurrentUserContext.setForTests("admin-1", List.of("USER"));
        GroupMember adminMembership = existingMember("group-1", "admin-1", MemberRole.ADMIN, MemberStatus.APPROVED);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "admin-1")).thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "target-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.removeMember("group-1", "target-1"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(groupMemberRepository, never()).delete(any());
    }

    // ---------- myGroups ----------

    @Test
    void myGroups_mapsMembershipsToGroupsAndPreservesTotalElements() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        GroupMember m1 = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        GroupMember m2 = existingMember("group-2", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        // totalElements (10) exceeds the page content size — verifies it's carried
        // through from the membership page rather than recomputed from `groups`.
        PageImpl<GroupMember> membershipPage = new PageImpl<>(List.of(m1, m2), pageable, 10);
        when(groupMemberRepository.findByUserIdAndStatus("member-1", MemberStatus.APPROVED, pageable)).thenReturn(membershipPage);

        Group group1 = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 3);
        Group group2 = existingGroup("group-2", "owner-2", GroupPrivacy.PUBLIC, 7);
        when(groupRepository.findAllById(List.of("group-1", "group-2"))).thenReturn(List.of(group1, group2));

        org.springframework.data.domain.Page<Group> result = groupService.myGroups(pageable);

        assertThat(result.getContent()).containsExactly(group1, group2);
        assertThat(result.getTotalElements()).isEqualTo(10);
    }

    @Test
    void myGroups_groupDeletedAfterMembershipCreated_isFilteredOutOfResults() {
        CurrentUserContext.setForTests("member-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 2);
        GroupMember m1 = existingMember("group-1", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        GroupMember m2 = existingMember("group-2", "member-1", MemberRole.MEMBER, MemberStatus.APPROVED);
        PageImpl<GroupMember> membershipPage = new PageImpl<>(List.of(m1, m2), pageable, 2);
        when(groupMemberRepository.findByUserIdAndStatus("member-1", MemberStatus.APPROVED, pageable)).thenReturn(membershipPage);

        Group group1 = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 3);
        // group-2 no longer exists.
        when(groupRepository.findAllById(List.of("group-1", "group-2"))).thenReturn(List.of(group1));

        org.springframework.data.domain.Page<Group> result = groupService.myGroups(pageable);

        assertThat(result.getContent()).containsExactly(group1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void myGroups_noAuthenticatedCaller_throwsUnauthorized() {
        assertThatThrownBy(() -> groupService.myGroups(PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- removeForModeration ----------

    @Test
    void removeForModeration_existing_deletesGroupAndAllMembers() {
        Group group = existingGroup("group-1", "owner-1", GroupPrivacy.PUBLIC, 5);
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));

        groupService.removeForModeration("group-1");

        verify(groupMemberRepository).deleteByGroupId("group-1");
        verify(groupRepository).delete(group);
    }

    @Test
    void removeForModeration_missing_isNoOp() {
        when(groupRepository.findById("missing")).thenReturn(Optional.empty());

        groupService.removeForModeration("missing");

        verify(groupMemberRepository, never()).deleteByGroupId(anyString());
        verify(groupRepository, never()).delete(any(Group.class));
    }
}
