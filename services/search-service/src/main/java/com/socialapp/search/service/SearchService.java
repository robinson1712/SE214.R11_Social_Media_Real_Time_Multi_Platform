package com.socialapp.search.service;

import com.socialapp.common.exception.BadRequestException;
import com.socialapp.search.client.FanpageServiceClient;
import com.socialapp.search.client.GroupServiceClient;
import com.socialapp.search.client.PostServiceClient;
import com.socialapp.search.client.UserServiceClient;
import com.socialapp.search.dto.SearchResults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserServiceClient userServiceClient;
    private final GroupServiceClient groupServiceClient;
    private final FanpageServiceClient fanpageServiceClient;
    private final PostServiceClient postServiceClient;

    /**
     * Federated search: fans out to the four services in sequence (small,
     * bounded per-category result counts, so parallelizing wasn't worth the
     * extra complexity) and returns whatever each one had — each Feign client's
     * fallback factory degrades its own section to empty rather than failing
     * the whole search if that service is down.
     */
    public SearchResults search(String query, int limitPerCategory) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Search query must not be blank");
        }
        int size = limitPerCategory > 0 ? limitPerCategory : 5;

        return new SearchResults(
                userServiceClient.search(query, 0, size).data().content(),
                groupServiceClient.search(query, 0, size).data().content(),
                fanpageServiceClient.search(query, 0, size).data().content(),
                postServiceClient.search(query, 0, size).data().content()
        );
    }
}
