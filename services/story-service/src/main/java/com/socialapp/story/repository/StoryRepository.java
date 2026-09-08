package com.socialapp.story.repository;

import com.socialapp.story.document.Story;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StoryRepository extends MongoRepository<Story, String> {

    List<Story> findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(Collection<String> authorIds, Instant now);

    List<Story> findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(String authorId, Instant now);
}
