package com.socialapp.story.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.story.document.Story;
import com.socialapp.story.dto.CreateStoryRequest;
import com.socialapp.story.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping
    public ResponseEntity<ApiResponse<Story>> createStory(@Valid @RequestBody CreateStoryRequest request) {
        Story story = storyService.createStory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Story created", story));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<Story>>> getFeed() {
        return ResponseEntity.ok(ApiResponse.success(storyService.getFeed()));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<ApiResponse<List<Story>>> getStoriesByAuthor(@PathVariable String authorId) {
        return ResponseEntity.ok(ApiResponse.success(storyService.getStoriesByAuthor(authorId)));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Story>> viewStory(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(storyService.markViewed(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable String id) {
        storyService.deleteStory(id);
        return ResponseEntity.ok(ApiResponse.success("Story deleted", null));
    }
}
