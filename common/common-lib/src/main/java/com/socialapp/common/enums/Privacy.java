package com.socialapp.common.enums;

public enum Privacy {
    PUBLIC,
    FRIENDS,
    /** Visible only to the explicit allow-list on the post (Post.customAudienceUserIds). */
    CUSTOM,
    PRIVATE
}
