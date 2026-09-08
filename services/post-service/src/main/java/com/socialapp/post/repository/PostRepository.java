package com.socialapp.post.repository;

import com.socialapp.common.enums.Privacy;
import com.socialapp.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, String> {

    // Sort is supplied by the caller (pinned-first for author feeds) rather than
    // baked into the method name via OrderBy.
    Page<Post> findByAuthorId(String authorId, Pageable pageable);

    Page<Post> findByGroupIdOrderByCreatedAtDesc(String groupId, Pageable pageable);

    Page<Post> findByPageIdOrderByCreatedAtDesc(String pageId, Pageable pageable);

    List<Post> findByIdIn(List<String> ids);

    // Global search only ever searches PUBLIC posts — see PostService.searchPublicPosts.
    Page<Post> findByContentContainingIgnoreCaseAndPrivacy(String content, Privacy privacy, Pageable pageable);
}
