package com.socialapp.user.dto;

/** The current user's relationship to a specific other user — a finer-grained
  * view than the raw {@code FriendshipStatus} entity field, which alone can't
  * tell "I sent this pending request" apart from "they sent it to me". */
public enum RelationshipStatus {
    NONE,
    PENDING_SENT,
    PENDING_RECEIVED,
    FRIENDS
}
