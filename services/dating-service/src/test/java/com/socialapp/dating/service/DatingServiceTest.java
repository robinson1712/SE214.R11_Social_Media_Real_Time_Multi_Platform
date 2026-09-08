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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
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
 * Unit tests for DatingService, in particular the candidate compatibility
 * scoring algorithm (mutual gender fit + age fit + shared-interest Jaccard
 * similarity) and the swipe → reciprocal-like → match pipeline.
 */
@ExtendWith(MockitoExtension.class)
class DatingServiceTest {

    @Mock
    private DatingProfileRepository datingProfileRepository;
    @Mock
    private SwipeRepository swipeRepository;
    @Mock
    private MatchRepository matchRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private DatingService datingService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        datingService = new DatingService(datingProfileRepository, swipeRepository, matchRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private DatingProfile profile(String id, Gender gender, int age, GenderPreference wants,
                                   int minAge, int maxAge, List<String> interests) {
        return DatingProfile.builder()
                .id(id)
                .gender(gender)
                .birthDate(LocalDate.now().minusYears(age))
                .genderPreference(wants)
                .minAgePreference(minAge)
                .maxAgePreference(maxAge)
                .interests(interests)
                .photos(List.of())
                .active(true)
                .build();
    }

    @Test
    void upsertProfile_noExistingProfile_createsNewOne() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.empty());
        when(datingProfileRepository.save(any(DatingProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertProfileRequest request = new UpsertProfileRequest(
                Gender.FEMALE, LocalDate.now().minusYears(28), "Love hiking", List.of("hiking", "coffee"),
                20, 35, GenderPreference.MALE, List.of());

        DatingProfile saved = datingService.upsertProfile(request);

        assertThat(saved.getId()).isEqualTo("alice");
        assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(saved.getInterests()).containsExactly("hiking", "coffee");
        assertThat(saved.getGenderPreference()).isEqualTo(GenderPreference.MALE);
    }

    @Test
    void upsertProfile_existingProfile_overwritesFieldsInPlace() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        DatingProfile existing = profile("alice", Gender.FEMALE, 28, GenderPreference.MALE, 20, 35, List.of("old"));
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.of(existing));
        when(datingProfileRepository.save(any(DatingProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertProfileRequest request = new UpsertProfileRequest(
                Gender.FEMALE, LocalDate.now().minusYears(28), "Updated bio", List.of("new-interest"),
                22, 40, GenderPreference.ANY, List.of("photo1"));

        DatingProfile saved = datingService.upsertProfile(request);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getBio()).isEqualTo("Updated bio");
        assertThat(saved.getInterests()).containsExactly("new-interest");
        assertThat(saved.getMinAgePreference()).isEqualTo(22);
        assertThat(saved.getGenderPreference()).isEqualTo(GenderPreference.ANY);
    }

    @Test
    void getCandidates_noProfileForCaller_throwsResourceNotFound() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datingService.getCandidates(0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCandidates_mutuallyCompatible_scoresMatchTheDocumentedRubric() {
        // Same numbers as the live end-to-end run: 60 pts age (mutual fit) + 20 pts
        // interests (2 shared / 4 union * 40) = 80. This is a regression check
        // against real production output, not just the formula in isolation.
        CurrentUserContext.setForTests("alice", List.of("USER"));
        DatingProfile alice = profile("alice", Gender.FEMALE, 28, GenderPreference.MALE, 20, 35,
                List.of("hiking", "coffee", "reading"));
        DatingProfile bob = profile("bob", Gender.MALE, 30, GenderPreference.FEMALE, 20, 35,
                List.of("coffee", "hiking", "gaming"));
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.of(alice));
        when(datingProfileRepository.findCandidatePool(eq("alice"), any())).thenReturn(List.of(bob));

        Page<CandidateResponse> result = datingService.getCandidates(0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).profile().getId()).isEqualTo("bob");
        assertThat(result.getContent().get(0).compatibilityScore()).isEqualTo(80);
    }

    @Test
    void getCandidates_genderMismatch_isFilteredOutEntirely() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        DatingProfile alice = profile("alice", Gender.FEMALE, 28, GenderPreference.MALE, 20, 35, List.of());
        // carol only wants males too — alice (who wants males) isn't a candidate carol would
        // want, and alice is looking for men, not carol — mismatched in both directions.
        DatingProfile carol = profile("carol", Gender.FEMALE, 27, GenderPreference.MALE, 20, 35, List.of());
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.of(alice));
        when(datingProfileRepository.findCandidatePool(eq("alice"), any())).thenReturn(List.of(carol));

        Page<CandidateResponse> result = datingService.getCandidates(0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getCandidates_rankedHighestScoreFirstAndPaginated() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        DatingProfile alice = profile("alice", Gender.FEMALE, 28, GenderPreference.ANY, 20, 40,
                List.of("hiking", "coffee", "reading", "movies"));
        // perfectMatch: full interest overlap -> higher score than partialMatch.
        DatingProfile perfectMatch = profile("perfect", Gender.MALE, 30, GenderPreference.ANY, 20, 40,
                List.of("hiking", "coffee", "reading", "movies"));
        DatingProfile partialMatch = profile("partial", Gender.MALE, 30, GenderPreference.ANY, 20, 40,
                List.of("hiking"));
        when(datingProfileRepository.findById("alice")).thenReturn(Optional.of(alice));
        when(datingProfileRepository.findCandidatePool(eq("alice"), any()))
                .thenReturn(List.of(partialMatch, perfectMatch));

        Page<CandidateResponse> firstPage = datingService.getCandidates(0, 1);

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().get(0).profile().getId()).isEqualTo("perfect");

        Page<CandidateResponse> secondPage = datingService.getCandidates(1, 1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().get(0).profile().getId()).isEqualTo("partial");
    }

    @Test
    void swipe_alreadySwipedOnTarget_throwsConflict() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(swipeRepository.existsBySwiperIdAndTargetId("alice", "bob")).thenReturn(true);

        assertThatThrownBy(() -> datingService.swipe(new SwipeRequest("bob", SwipeAction.LIKE)))
                .isInstanceOf(ConflictException.class);

        verify(swipeRepository, never()).save(any());
    }

    @Test
    void swipe_likeWithNoReciprocalLike_recordsSwipeButNoMatch() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(swipeRepository.existsBySwiperIdAndTargetId("alice", "bob")).thenReturn(false);
        when(swipeRepository.findBySwiperIdAndTargetIdAndAction("bob", "alice", SwipeAction.LIKE))
                .thenReturn(Optional.empty());

        SwipeResponse response = datingService.swipe(new SwipeRequest("bob", SwipeAction.LIKE));

        assertThat(response.swiped()).isTrue();
        assertThat(response.matched()).isFalse();
        verify(swipeRepository).save(any(Swipe.class));
        verify(matchRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void swipe_reciprocalLike_createsNormalizedMatchAndPublishesEvent() {
        CurrentUserContext.setForTests("bob", List.of("USER"));
        when(swipeRepository.existsBySwiperIdAndTargetId("bob", "alice")).thenReturn(false);
        when(swipeRepository.findBySwiperIdAndTargetIdAndAction("alice", "bob", SwipeAction.LIKE))
                .thenReturn(Optional.of(mock(Swipe.class)));
        when(matchRepository.findByUser1IdAndUser2Id(anyString(), anyString())).thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        SwipeResponse response = datingService.swipe(new SwipeRequest("alice", SwipeAction.LIKE));

        assertThat(response.matched()).isTrue();

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        // ids are normalized lexicographically regardless of who swiped last.
        assertThat(matchCaptor.getValue().getUser1Id()).isEqualTo("alice");
        assertThat(matchCaptor.getValue().getUser2Id()).isEqualTo("bob");

        ArgumentCaptor<MatchEvent> eventCaptor = ArgumentCaptor.forClass(MatchEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.MATCH), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().user1Id()).isEqualTo("alice");
        assertThat(eventCaptor.getValue().user2Id()).isEqualTo("bob");
    }

    @Test
    void swipe_reciprocalLikeButMatchAlreadyExists_doesNotCreateDuplicate() {
        CurrentUserContext.setForTests("bob", List.of("USER"));
        when(swipeRepository.existsBySwiperIdAndTargetId("bob", "alice")).thenReturn(false);
        when(swipeRepository.findBySwiperIdAndTargetIdAndAction("alice", "bob", SwipeAction.LIKE))
                .thenReturn(Optional.of(mock(Swipe.class)));
        when(matchRepository.findByUser1IdAndUser2Id("alice", "bob")).thenReturn(Optional.of(mock(Match.class)));

        SwipeResponse response = datingService.swipe(new SwipeRequest("alice", SwipeAction.LIKE));

        assertThat(response.matched()).isTrue();
        verify(matchRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void swipe_passAction_neverChecksForMatch() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(swipeRepository.existsBySwiperIdAndTargetId("alice", "bob")).thenReturn(false);

        SwipeResponse response = datingService.swipe(new SwipeRequest("bob", SwipeAction.PASS));

        assertThat(response.matched()).isFalse();
        verify(swipeRepository, never()).findBySwiperIdAndTargetIdAndAction(anyString(), anyString(), any());
    }

    @Test
    void getMatches_delegatesToRepositoryForCurrentUser() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        Page<Match> expected = org.springframework.data.domain.Page.empty();
        when(matchRepository.findByUser1IdOrUser2Id("alice", "alice", pageable)).thenReturn(expected);

        Page<Match> result = datingService.getMatches(pageable);

        assertThat(result).isSameAs(expected);
    }

    // ---------- unmatch ----------

    private Match match(String id, String user1Id, String user2Id) {
        return Match.builder().id(id).user1Id(user1Id).user2Id(user2Id).build();
    }

    @Test
    void unmatch_byUser1_deletesMatch() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        Match m = match("match-1", "alice", "bob");
        when(matchRepository.findById("match-1")).thenReturn(Optional.of(m));

        datingService.unmatch("match-1");

        verify(matchRepository).delete(m);
    }

    @Test
    void unmatch_byUser2_deletesMatch() {
        CurrentUserContext.setForTests("bob", List.of("USER"));
        Match m = match("match-1", "alice", "bob");
        when(matchRepository.findById("match-1")).thenReturn(Optional.of(m));

        datingService.unmatch("match-1");

        verify(matchRepository).delete(m);
    }

    @Test
    void unmatch_byOutsider_throwsForbiddenAndNeverDeletes() {
        CurrentUserContext.setForTests("stranger", List.of("USER"));
        Match m = match("match-1", "alice", "bob");
        when(matchRepository.findById("match-1")).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> datingService.unmatch("match-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(matchRepository, never()).delete(any());
    }

    @Test
    void unmatch_missingMatch_throwsResourceNotFound() {
        when(matchRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datingService.unmatch("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
