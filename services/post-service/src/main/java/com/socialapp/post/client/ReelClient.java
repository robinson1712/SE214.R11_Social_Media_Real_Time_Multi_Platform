package com.socialapp.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** Used only by PostService.shareReel to bump the original reel's share
 * count — same reasoning as UserServiceClient: a cross-service call kept
 * to the one thing post-service actually needs from reels-service. */
@FeignClient(name = "reels-service", fallbackFactory = ReelClientFallbackFactory.class)
public interface ReelClient {

    @PostMapping("/api/reels/{id}/share-count")
    void incrementShareCount(@PathVariable("id") String id);
}
