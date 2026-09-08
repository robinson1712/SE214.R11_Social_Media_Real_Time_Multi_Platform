package com.socialapp.search.service;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.search.client.FanpageServiceClient;
import com.socialapp.search.client.GroupServiceClient;
import com.socialapp.search.client.PostServiceClient;
import com.socialapp.search.client.UserServiceClient;
import com.socialapp.search.dto.GroupSearchResult;
import com.socialapp.search.dto.PageSearchResult;
import com.socialapp.search.dto.PostSearchResult;
import com.socialapp.search.dto.SearchResults;
import com.socialapp.search.dto.UserSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchService — all four Feign clients are mocked, so these
 * exercise only the service's own decisions (fan-out, blank-query rejection,
 * default limit).
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private GroupServiceClient groupServiceClient;
    @Mock
    private FanpageServiceClient fanpageServiceClient;
    @Mock
    private PostServiceClient postServiceClient;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(userServiceClient, groupServiceClient, fanpageServiceClient, postServiceClient);
    }

    private static <T> ApiResponse<PageResponse<T>> pageOf(List<T> content) {
        return ApiResponse.success(new PageResponse<>(content, 0, content.size(), content.size(), 1, true));
    }

    @Test
    void search_aggregatesAllFourCategories() {
        UserSearchResult user = new UserSearchResult("user-1", "Alice", null);
        GroupSearchResult group = new GroupSearchResult("group-1", "Alice's Group", null, null);
        PageSearchResult page = new PageSearchResult("page-1", "Alice's Page", null, null);
        PostSearchResult post = new PostSearchResult("post-1", "user-1", "hi alice", Instant.now());

        when(userServiceClient.search("alice", 0, 5)).thenReturn(pageOf(List.of(user)));
        when(groupServiceClient.search("alice", 0, 5)).thenReturn(pageOf(List.of(group)));
        when(fanpageServiceClient.search("alice", 0, 5)).thenReturn(pageOf(List.of(page)));
        when(postServiceClient.search("alice", 0, 5)).thenReturn(pageOf(List.of(post)));

        SearchResults result = searchService.search("alice", 5);

        assertThat(result.users()).containsExactly(user);
        assertThat(result.groups()).containsExactly(group);
        assertThat(result.pages()).containsExactly(page);
        assertThat(result.posts()).containsExactly(post);
    }

    @Test
    void search_blankQuery_throwsBadRequest() {
        assertThatThrownBy(() -> searchService.search("   ", 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_nullQuery_throwsBadRequest() {
        assertThatThrownBy(() -> searchService.search(null, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_zeroLimit_fallsBackToDefaultOfFive() {
        when(userServiceClient.search("q", 0, 5)).thenReturn(pageOf(List.of()));
        when(groupServiceClient.search("q", 0, 5)).thenReturn(pageOf(List.of()));
        when(fanpageServiceClient.search("q", 0, 5)).thenReturn(pageOf(List.of()));
        when(postServiceClient.search("q", 0, 5)).thenReturn(pageOf(List.of()));

        searchService.search("q", 0);

        // Verified implicitly by the stub matching size=5 above — a mismatched
        // size would leave those stubs unmatched and the client would NPE on
        // the unstubbed call's null return.
    }

    @Test
    void search_customLimit_isPassedThroughToEachClient() {
        when(userServiceClient.search("q", 0, 10)).thenReturn(pageOf(List.of()));
        when(groupServiceClient.search("q", 0, 10)).thenReturn(pageOf(List.of()));
        when(fanpageServiceClient.search("q", 0, 10)).thenReturn(pageOf(List.of()));
        when(postServiceClient.search("q", 0, 10)).thenReturn(pageOf(List.of()));

        SearchResults result = searchService.search("q", 10);

        assertThat(result.users()).isEmpty();
    }
}
