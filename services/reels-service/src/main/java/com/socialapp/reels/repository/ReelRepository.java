package com.socialapp.reels.repository;

import com.socialapp.reels.document.Reel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReelRepository extends MongoRepository<Reel, String> {

    Page<Reel> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Reel> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);
}
