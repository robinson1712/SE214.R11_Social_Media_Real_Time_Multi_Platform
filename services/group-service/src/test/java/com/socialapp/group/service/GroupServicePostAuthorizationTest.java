package com.socialapp.group.service;

import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.entity.Group;
import com.socialapp.group.entity.MemberRole;
import com.socialapp.group.entity.MemberStatus;
import com.socialapp.group.entity.GroupPrivacy;
import com.socialapp.group.repository.GroupMemberRepository;
import com.socialapp.group.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServicePostAuthorizationTest {

    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks GroupService groupService;

    @Test
    void approvedMemberMayCreateGroupPost() {
        when(groupRepository.existsById("group-1")).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1"))
                .thenReturn(Optional.of(member(MemberStatus.APPROVED)));

        assertThat(groupService.canCreatePost("group-1", "member-1")).isTrue();
    }

    @Test
    void pendingMemberCannotCreateGroupPost() {
        when(groupRepository.existsById("group-1")).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1"))
                .thenReturn(Optional.of(member(MemberStatus.PENDING)));

        assertThat(groupService.canCreatePost("group-1", "member-1")).isFalse();
    }

    @Test
    void kickedOrFormerMemberCannotCreateGroupPost() {
        when(groupRepository.existsById("group-1")).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1"))
                .thenReturn(Optional.empty());

        assertThat(groupService.canCreatePost("group-1", "member-1")).isFalse();
    }

    @Test
    void missingGroupCannotCreatePostAndDoesNotQueryMembership() {
        when(groupRepository.existsById("missing")).thenReturn(false);

        assertThat(groupService.canCreatePost("missing", "member-1")).isFalse();
        verify(groupMemberRepository, never()).findByGroupIdAndUserId("missing", "member-1");
    }

    @Test
    void privateGroupContentIsVisibleOnlyToApprovedMembers() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(
                Group.builder().id("group-1").privacy(GroupPrivacy.PRIVATE).build()));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "member-1"))
                .thenReturn(Optional.of(member(MemberStatus.APPROVED)));
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "former-member"))
                .thenReturn(Optional.empty());
        when(groupMemberRepository.findByGroupIdAndUserId("group-1", "pending-member"))
                .thenReturn(Optional.of(member(MemberStatus.PENDING)));

        assertThat(groupService.canViewContent("group-1", "member-1")).isTrue();
        assertThat(groupService.canViewContent("group-1", "former-member")).isFalse();
        assertThat(groupService.canViewContent("group-1", "pending-member")).isFalse();
        assertThat(groupService.canViewContent("group-1", null)).isFalse();
    }

    @Test
    void publicGroupContentIsVisibleWithoutMembership() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(
                Group.builder().id("group-1").privacy(GroupPrivacy.PUBLIC).build()));

        assertThat(groupService.canViewContent("group-1", null)).isTrue();
        verify(groupMemberRepository, never()).findByGroupIdAndUserId("group-1", null);
    }

    private GroupMember member(MemberStatus status) {
        return GroupMember.builder().groupId("group-1").userId("member-1")
                .role(MemberRole.MEMBER).status(status).build();
    }
}
