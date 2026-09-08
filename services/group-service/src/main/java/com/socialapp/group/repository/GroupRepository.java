package com.socialapp.group.repository;

import com.socialapp.group.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, String> {

    /**
     * A group is visible to the caller if it's PUBLIC, or if it's PRIVATE and the
     * caller has an APPROVED membership in it. When callerId is null (no
     * authenticated caller), the membership subquery matches nothing, so this
     * degrades to PUBLIC-only — the same behavior as before this method existed.
     * <p>
     * {@code name} must be "" rather than null when unfiltered — the Postgres
     * JDBC driver can't infer a text type for a null bind parameter used only
     * inside an IS NULL check (it falls back to bytea, which then blows up the
     * very next LOWER(...) usage of the same parameter with "function lower(bytea)
     * does not exist"). An empty string sidesteps the type-inference problem
     * entirely instead of relying on an explicit CAST.
     */
    @Query("""
            SELECT g FROM SocialGroup g
            WHERE (g.privacy = com.socialapp.group.entity.GroupPrivacy.PUBLIC
                   OR g.id IN (
                       SELECT gm.groupId FROM GroupMember gm
                       WHERE gm.userId = :callerId
                         AND gm.status = com.socialapp.group.entity.MemberStatus.APPROVED
                   ))
              AND (:name = '' OR LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%')))
            """)
    Page<Group> findVisibleGroups(@Param("callerId") String callerId, @Param("name") String name, Pageable pageable);
}
