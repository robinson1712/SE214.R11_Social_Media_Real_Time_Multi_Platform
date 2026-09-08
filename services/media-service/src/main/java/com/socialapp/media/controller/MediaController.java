package com.socialapp.media.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.media.dto.MediaFileResponse;
import com.socialapp.media.dto.UploadResponse;
import com.socialapp.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        UploadResponse response = mediaService.upload(CurrentUserContext.getUserId(), file, purpose);
        return ResponseEntity.ok(ApiResponse.success("File uploaded", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        mediaService.delete(CurrentUserContext.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("File deleted", null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaFileResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(mediaService.get(id)));
    }
}
