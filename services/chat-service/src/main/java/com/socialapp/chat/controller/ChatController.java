package com.socialapp.chat.controller;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.Message;
import com.socialapp.chat.dto.CreateConversationRequest;
import com.socialapp.chat.dto.PresenceBatchRequest;
import com.socialapp.chat.service.ChatService;
import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<Conversation>> createConversation(@RequestBody CreateConversationRequest request) {
        Conversation conversation = chatService.createConversation(request.participantIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(conversation));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<Conversation>>> getConversations() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversations(CurrentUserContext.getUserId())));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<PageResponse<Message>>> getMessages(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.from(chatService.getMessages(id, CurrentUserContext.getUserId(), pageable))));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable String id) {
        chatService.markRead(id, CurrentUserContext.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<ApiResponse<Message>> deleteMessage(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Message deleted", chatService.deleteMessage(id, CurrentUserContext.getUserId())));
    }

    @GetMapping("/presence/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getPresence(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("online", chatService.isOnline(userId))));
    }

    @PostMapping("/presence/batch")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getPresenceBatch(@RequestBody PresenceBatchRequest request) {
        List<String> userIds = request.userIds() != null ? request.userIds() : List.of();
        return ResponseEntity.ok(ApiResponse.success(chatService.onlineStatuses(userIds)));
    }
}
