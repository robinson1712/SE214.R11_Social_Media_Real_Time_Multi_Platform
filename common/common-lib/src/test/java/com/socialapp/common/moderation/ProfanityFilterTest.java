package com.socialapp.common.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProfanityFilterTest {

    @Test
    void cleanText_isNotFlagged() {
        assertThat(ProfanityFilter.containsProfanity("Hello world, this is a lovely sunny day!")).isFalse();
        assertThat(ProfanityFilter.containsProfanity("Hôm nay trời đẹp quá, đi chơi thôi!")).isFalse();
    }

    @Test
    void nullOrBlank_isNotFlagged() {
        assertThat(ProfanityFilter.containsProfanity(null)).isFalse();
        assertThat(ProfanityFilter.containsProfanity("")).isFalse();
        assertThat(ProfanityFilter.containsProfanity("   ")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"You are such a bitch", "what the fuck is this", "SHIT happens"})
    void englishProfanity_isFlaggedCaseInsensitively(String text) {
        assertThat(ProfanityFilter.containsProfanity(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"đồ con điếm", "thằng chó này ngu vãi", "địt con mẹ mày"})
    void vietnameseProfanity_isFlagged(String text) {
        assertThat(ProfanityFilter.containsProfanity(text)).isTrue();
    }

    @Test
    void evasionWithPunctuationOrSpacing_isStillCaught() {
        assertThat(ProfanityFilter.containsProfanity("f.u.c.k this")).isTrue();
        assertThat(ProfanityFilter.containsProfanity("f u c k this")).isTrue();
    }

    @Test
    void evasionWithVietnameseDiacriticSpacing_isStillCaught() {
        // "đ ụ" with a space inserted mid-word — normalize() strips both the
        // diacritic-as-combining-mark and the inserted whitespace.
        assertThat(ProfanityFilter.containsProfanity("đ ụ má mày")).isTrue();
    }

    @Test
    void unrelatedSentenceWithNoBannedTerm_isNotFlagged() {
        assertThat(ProfanityFilter.containsProfanity("The quick brown fox jumps over the lazy dog")).isFalse();
    }

    @Test
    void knownLimitation_substringMatchCanFalsePositiveOnInnocentWords() {
        // The "Scunthorpe problem": stripping spaces/punctuation to catch spaced-out
        // evasion ("f u c k") is fundamentally in tension with avoiding substring
        // false positives on innocent words that happen to contain a banned term
        // (the town of Scunthorpe contains "cunt"). This is a documented, accepted
        // trade-off for a simple word-list filter — not something this test suite
        // pretends is solved. See ProfanityFilter's class javadoc.
        assertThat(ProfanityFilter.containsProfanity("Scunthorpe is a town in England")).isTrue();
    }
}
