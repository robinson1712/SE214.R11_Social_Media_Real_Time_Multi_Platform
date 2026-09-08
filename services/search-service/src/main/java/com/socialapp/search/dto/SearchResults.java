package com.socialapp.search.dto;

import java.util.List;

public record SearchResults(
        List<UserSearchResult> users,
        List<GroupSearchResult> groups,
        List<PageSearchResult> pages,
        List<PostSearchResult> posts
) {
}
