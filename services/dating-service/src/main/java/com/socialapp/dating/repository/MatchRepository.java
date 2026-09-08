package com.socialapp.dating.repository;

import com.socialapp.dating.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, String> {

    Optional<Match> findByUser1IdAndUser2Id(String user1Id, String user2Id);

    Page<Match> findByUser1IdOrUser2Id(String user1Id, String user2Id, Pageable pageable);
}
