package com.socialapp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bootstraps the ADMIN role from a config-driven allowlist rather than a
 * promote-another-user endpoint — there's no chicken-and-egg problem (an
 * endpoint like that would itself need to be admin-gated) and no manual DB
 * edit needs documenting in the README. Whoever registers with one of these
 * emails gets ADMIN in addition to USER; everyone else just gets USER.
 */
@Component
public class AdminEmailAllowlist {

    private final Set<String> emails;

    public AdminEmailAllowlist(@Value("${moderation.admin-emails:}") String rawCommaSeparated) {
        this.emails = Arrays.stream(rawCommaSeparated.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && emails.contains(email.toLowerCase());
    }
}
