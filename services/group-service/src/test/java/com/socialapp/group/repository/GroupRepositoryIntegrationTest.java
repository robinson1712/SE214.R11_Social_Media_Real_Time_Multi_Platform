package com.socialapp.group.repository;

import com.socialapp.group.entity.Group;
import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.entity.GroupPrivacy;
import com.socialapp.group.entity.MemberRole;
import com.socialapp.group.entity.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test running the real {@code findVisibleGroups} JPQL query
 * against an actual Postgres instance (via Testcontainers) — this is the exact
 * query that broke in production with "function lower(bytea) does not exist"
 * when called with a null name parameter (see TODO.md, "Lỗi thứ 7"). A
 * mocked-repository unit test (see GroupServiceTest) can never catch this
 * class of bug because the query text never actually executes there; only a
 * real database can validate that the JPQL is both syntactically valid and
 * semantically correct.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GroupRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private Group aGroup(String name, GroupPrivacy privacy, String ownerId) {
        return Group.builder()
                .name(name)
                .privacy(privacy)
                .ownerId(ownerId)
                .memberCount(1)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void findVisibleGroups_withNoFilters_doesNotThrowAndReturnsPublicGroup() {
        // The "" sentinel (not null) is what production code passes for "no filter" —
        // this is the regression test for the real bug: a null bind parameter used
        // only in an IS NULL check defeated Postgres's JDBC type inference and broke
        // the query's LOWER(...) usage with "function lower(bytea) does not exist".
        Group publicGroup = groupRepository.save(aGroup("Public Hiking Club", GroupPrivacy.PUBLIC, "owner-1"));

        Page<Group> page = groupRepository.findVisibleGroups("", "", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Group::getId).contains(publicGroup.getId());
    }

    @Test
    void findVisibleGroups_privateGroup_visibleOnlyToApprovedMember() {
        Group privateGroup = groupRepository.save(aGroup("Secret Society", GroupPrivacy.PRIVATE, "owner-2"));
        groupMemberRepository.save(GroupMember.builder()
                .groupId(privateGroup.getId())
                .userId("member-1")
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .joinedAt(Instant.now())
                .build());

        Page<Group> visibleToMember = groupRepository.findVisibleGroups("member-1", "", PageRequest.of(0, 20));
        Page<Group> visibleToStranger = groupRepository.findVisibleGroups("someone-else", "", PageRequest.of(0, 20));
        Page<Group> visibleToAnonymous = groupRepository.findVisibleGroups("", "", PageRequest.of(0, 20));

        assertThat(visibleToMember.getContent()).extracting(Group::getId).contains(privateGroup.getId());
        assertThat(visibleToStranger.getContent()).extracting(Group::getId).doesNotContain(privateGroup.getId());
        assertThat(visibleToAnonymous.getContent()).extracting(Group::getId).doesNotContain(privateGroup.getId());
    }

    @Test
    void findVisibleGroups_privateGroup_notVisibleToPendingMember() {
        // A PENDING (not yet approved) membership must not grant visibility.
        Group privateGroup = groupRepository.save(aGroup("Awaiting Approval", GroupPrivacy.PRIVATE, "owner-3"));
        groupMemberRepository.save(GroupMember.builder()
                .groupId(privateGroup.getId())
                .userId("pending-user")
                .role(MemberRole.MEMBER)
                .status(MemberStatus.PENDING)
                .build());

        Page<Group> result = groupRepository.findVisibleGroups("pending-user", "", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Group::getId).doesNotContain(privateGroup.getId());
    }

    @Test
    void findVisibleGroups_nameFilter_isCaseInsensitiveSubstringMatch() {
        groupRepository.save(aGroup("Weekend Photographers", GroupPrivacy.PUBLIC, "owner-4"));
        groupRepository.save(aGroup("Book Club", GroupPrivacy.PUBLIC, "owner-5"));

        Page<Group> result = groupRepository.findVisibleGroups("", "PHOTO", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Weekend Photographers");
    }

    @Test
    void findVisibleGroups_tableAndEntityNamesAreCorrect() {
        // Regression check for the other real fix bundled with this query: the JPA
        // entity name was changed from the default "Group" to "SocialGroup" to avoid
        // colliding with the GROUP BY keyword in JPQL grammar, while the underlying
        // SQL table stays "groups". If either mapping were wrong, every save() above
        // would already have failed — this test just makes that assumption explicit.
        Group saved = groupRepository.save(aGroup("Mapping Sanity Check", GroupPrivacy.PUBLIC, "owner-6"));

        assertThat(groupRepository.findById(saved.getId())).isPresent();
    }
}
