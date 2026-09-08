package com.socialapp.common.moderation;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * First line of defense for user-generated text (post/comment/reel captions,
 * story captions, group/page descriptions...): a local, dependency-free
 * word-list filter. It runs synchronously at creation time in the owning
 * service (see post-service/comment-service) and rejects the request outright
 * — no external moderation API, no network call, so it can't add latency or
 * become a point of failure for the write path.
 * <p>
 * This deliberately only catches the easy, high-confidence case (known slurs
 * and profanity, including simple leetspeak/spacing evasion). It is not a
 * substitute for the human-review path in moderation-service — that's what
 * the report queue is for.
 * <p>
 * Known trade-off: stripping spaces/punctuation to catch spaced-out evasion
 * ("f u c k") means this does substring matching, not whole-word matching —
 * an innocent word that happens to contain a banned term as a substring (the
 * classic example: the town "Scunthorpe" contains "cunt") will false-positive.
 * This is a standard, accepted limitation of simple word-list filters, not
 * something worth the complexity of "fixing" here.
 */
public final class ProfanityFilter {

    private ProfanityFilter() {
    }

    // A moderate, representative word list — this is a demonstration of the
    // filtering mechanism, not an attempt at an exhaustive slur database.
    // Real deployments would swap this for a maintained, regularly-updated list.
    private static final Set<String> BANNED_TERMS = Set.of(
            // English
            "fuck", "shit", "bitch", "asshole", "bastard", "nigger", "faggot", "cunt", "whore", "slut",
            // Vietnamese
            "đụ", "đéo", "địt", "lồn", "cặc", "đĩ", "đĩ mẹ", "con điếm", "thằng chó", "đồ khốn", "óc chó", "ngu như chó"
    );

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * True if the text contains a banned term. Normalizes case and
     * punctuation/whitespace first so trivial spacing evasion ("f.u.c.k",
     * "đ ụ má mày") is still caught. Deliberately does <b>not</b> strip
     * diacritics: Vietnamese tone marks change meaning entirely (this filter
     * used to normalize "đi" — go — down to the same string as "đĩ" — a slur —
     * once the tone marks were stripped, flagging completely innocent text).
     * NFC composition just guards against the rare case where a client sends
     * pre-decomposed Unicode (base letter + separate combining mark) instead
     * of the usual single-codepoint precomposed form.
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (String term : BANNED_TERMS) {
            if (normalized.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        String composed = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFC);
        return NON_ALNUM.matcher(composed).replaceAll("");
    }
}
