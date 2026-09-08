package com.socialapp.group.repository;

import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.entity.MemberRole;
import com.socialapp.group.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, String> {

    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    Page<GroupMember> findByGroupIdAndStatus(String groupId, MemberStatus status, Pageable pageable);

    Page<GroupMember> findByUserIdAndStatus(String userId, MemberStatus status, Pageable pageable);

    long countByGroupIdAndRoleAndStatus(String groupId, MemberRole role, MemberStatus status);

    void deleteByGroupId(String groupId);
}
