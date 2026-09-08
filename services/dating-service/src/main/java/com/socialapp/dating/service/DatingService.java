package com.socialapp.dating.service;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.MatchEvent;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.dating.dto.CandidateResponse;
import com.socialapp.dating.dto.SwipeRequest;
import com.socialapp.dating.dto.SwipeResponse;
import com.socialapp.dating.dto.UpsertProfileRequest;
import com.socialapp.dating.entity.DatingProfile;
import com.socialapp.dating.entity.Gender;
import com.socialapp.dating.entity.GenderPreference;
import com.socialapp.dating.entity.Match;
import com.socialapp.dating.entity.Swipe;
import com.socialapp.dating.entity.SwipeAction;
import com.socialapp.dating.repository.DatingProfileRepository;
import com.socialapp.dating.repository.MatchRepository;
import com.socialapp.dating.repository.SwipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DatingService {

    /** How far back into the not-yet-swiped pool a candidates read is willing to score and rank. */
    private static final int CANDIDATE_POOL_SIZE = 300;

    private final DatingProfileRepository datingProfileRepository;
    private final SwipeRepository swipeRepository;
    private final MatchRepository matchRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DatingProfile upsertProfile(UpsertProfileRequest request) {
        String userId = CurrentUserContext.getUserId();
        DatingProfile profile = datingProfileRepository.findById(userId).orElse(null);
        if (profile == null) {
            profile = DatingProfile.builder()
                    .id(userId)
                    .build();
        }
        profile.setGender(request.gender());
        profile.setBirthDate(request.birthDate());
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.interests() != null) {
            profile.setInterests(new ArrayList<>(request.interests()));
        }
        profile.setMinAgePreference(request.minAgePreference());
        profile.setMaxAgePreference(request.maxAgePreference());
        profile.setGenderPreference(request.genderPreference() != null ? request.genderPreference() : GenderPreference.ANY);
        if (request.photos() != null) {
            profile.setPhotos(new ArrayList<>(request.photos()));
        }
        return datingProfileRepository.save(profile);
    }

    public DatingProfile getMyProfile() {
        String userId = CurrentUserContext.getUserId();
        return datingProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Dating profile not found for current user"));
    }

    /**
     * Candidate matching, in two stages:
     * <ol>
     *   <li>SQL narrows the field to active, not-self, not-already-swiped profiles
     *       (a plain LIMIT-capped pool, not a real page — see the repository).</li>
     *   <li>Java filters that pool to mutually gender-compatible candidates, scores
     *       each one 0-100 on age fit + shared interests, sorts by score, and only
     *       then paginates the ranked result. This is the same "bounded pool → score
     *       → sort → paginate in memory" shape used for engagement-ranked feed reads
     *       in feed-service — reasonable at this scale, and it keeps the ranking
     *       formula free to change without needing a database migration.</li>
     * </ol>
     */
    public Page<CandidateResponse> getCandidates(int page, int size) {
        String userId = CurrentUserContext.getUserId();
        DatingProfile self = datingProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Create your dating profile before browsing candidates"));

        List<DatingProfile> pool = datingProfileRepository.findCandidatePool(
                userId, PageRequest.of(0, CANDIDATE_POOL_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<CandidateResponse> ranked = pool.stream()
                .filter(candidate -> isGenderCompatible(self, candidate))
                .map(candidate -> new CandidateResponse(candidate, compatibilityScore(self, candidate)))
                .sorted(Comparator.comparingInt(CandidateResponse::compatibilityScore).reversed())
                .toList();

        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(ranked.subList(from, to), pageable, ranked.size());
    }

    private boolean isGenderCompatible(DatingProfile self, DatingProfile candidate) {
        return wants(self.getGenderPreference(), candidate.getGender())
                && wants(candidate.getGenderPreference(), self.getGender());
    }

    private boolean wants(GenderPreference preference, Gender gender) {
        if (preference == null || preference == GenderPreference.ANY) {
            return true;
        }
        return preference.name().equals(gender == null ? null : gender.name());
    }

    /**
     * 0-100 compatibility score: up to 60 points for age fit (40 if the
     * candidate falls in the caller's preferred range, another 20 if the
     * reverse is also true), up to 40 points for shared interests (Jaccard
     * similarity between the two interest sets, scaled to 40).
     */
    private int compatibilityScore(DatingProfile self, DatingProfile candidate) {
        int score = 0;

        Integer selfAge = ageOf(self.getBirthDate());
        Integer candidateAge = ageOf(candidate.getBirthDate());
        if (candidateAge != null && candidateAge >= self.getMinAgePreference() && candidateAge <= self.getMaxAgePreference()) {
            score += 40;
        }
        if (selfAge != null && selfAge >= candidate.getMinAgePreference() && selfAge <= candidate.getMaxAgePreference()) {
            score += 20;
        }

        Set<String> mine = new HashSet<>(self.getInterests());
        Set<String> theirs = new HashSet<>(candidate.getInterests());
        if (!mine.isEmpty() && !theirs.isEmpty()) {
            Set<String> intersection = new HashSet<>(mine);
            intersection.retainAll(theirs);
            Set<String> union = new HashSet<>(mine);
            union.addAll(theirs);
            score += Math.round(40.0f * intersection.size() / union.size());
        }

        return score;
    }

    private Integer ageOf(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }

    public SwipeResponse swipe(SwipeRequest request) {
        String swiperId = CurrentUserContext.getUserId();
        String targetId = request.targetId();

        if (swipeRepository.existsBySwiperIdAndTargetId(swiperId, targetId)) {
            throw new ConflictException("You already swiped on this user");
        }

        Swipe swipe = Swipe.builder()
                .swiperId(swiperId)
                .targetId(targetId)
                .action(request.action())
                .build();
        swipeRepository.save(swipe);

        boolean matched = false;
        if (request.action() == SwipeAction.LIKE) {
            boolean reciprocalLike = swipeRepository
                    .findBySwiperIdAndTargetIdAndAction(targetId, swiperId, SwipeAction.LIKE)
                    .isPresent();
            if (reciprocalLike) {
                matched = true;
                String user1Id = swiperId.compareTo(targetId) < 0 ? swiperId : targetId;
                String user2Id = swiperId.compareTo(targetId) < 0 ? targetId : swiperId;
                if (matchRepository.findByUser1IdAndUser2Id(user1Id, user2Id).isEmpty()) {
                    Match match = Match.builder()
                            .user1Id(user1Id)
                            .user2Id(user2Id)
                            .build();
                    Match saved = matchRepository.save(match);
                    MatchEvent event = new MatchEvent(saved.getId(), saved.getUser1Id(), saved.getUser2Id(), Instant.now());
                    kafkaTemplate.send(KafkaTopics.MATCH, saved.getId(), event);
                }
            }
        }

        return new SwipeResponse(true, matched);
    }

    public Page<Match> getMatches(Pageable pageable) {
        String userId = CurrentUserContext.getUserId();
        return matchRepository.findByUser1IdOrUser2Id(userId, userId, pageable);
    }

    public void unmatch(String matchId) {
        String userId = CurrentUserContext.getUserId();
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        if (!match.getUser1Id().equals(userId) && !match.getUser2Id().equals(userId)) {
            throw new ForbiddenException("You are not part of this match");
        }
        matchRepository.delete(match);
    }
}
