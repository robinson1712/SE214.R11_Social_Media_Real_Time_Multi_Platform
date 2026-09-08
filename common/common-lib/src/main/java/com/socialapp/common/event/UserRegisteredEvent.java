package com.socialapp.common.event;

import java.time.Instant;
import java.time.LocalDate;

/** gender/dob are optional, collected at signup — passed as a plain string
  * (not user-service's Gender enum) since common-lib must stay independent
  * of any one service's domain types; user-service parses it defensively. */
public record UserRegisteredEvent(
        String userId,
        String email,
        String fullName,
        String gender,
        LocalDate dob,
        Instant occurredAt
) {
}
