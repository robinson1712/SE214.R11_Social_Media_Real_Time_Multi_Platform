package com.socialapp.reaction.service;

import com.socialapp.common.enums.TargetType;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.reaction.dto.SaveItemRequest;
import com.socialapp.reaction.entity.SavedItem;
import com.socialapp.reaction.repository.SavedItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SavedItemService. The repository is mocked;
 * CurrentUserContext is set/cleared per test via the test-support seam in
 * common-lib rather than a real HTTP request.
 */
@ExtendWith(MockitoExtension.class)
class SavedItemServiceTest {

    @Mock
    private SavedItemRepository savedItemRepository;

    private SavedItemService savedItemService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        savedItemService = new SavedItemService(savedItemRepository);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private SavedItem existingSavedItem(String id, String userId) {
        return SavedItem.builder()
                .id(id)
                .targetType(TargetType.POST)
                .targetId("post-1")
                .targetOwnerId("post-owner-1")
                .userId(userId)
                .build();
    }

    @Test
    void save_notAlreadySaved_createsNewRow() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        SaveItemRequest request = new SaveItemRequest(TargetType.POST, "post-1", "post-owner-1");
        when(savedItemRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.empty());
        when(savedItemRepository.save(any(SavedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedItem saved = savedItemService.save(request);

        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getTargetId()).isEqualTo("post-1");
        assertThat(saved.getTargetOwnerId()).isEqualTo("post-owner-1");
    }

    @Test
    void save_alreadySaved_returnsExistingRowWithoutInsertingAgain() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        SavedItem existing = existingSavedItem("saved-1", "user-1");
        SaveItemRequest request = new SaveItemRequest(TargetType.POST, "post-1", "post-owner-1");
        when(savedItemRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.of(existing));

        SavedItem saved = savedItemService.save(request);

        assertThat(saved.getId()).isEqualTo("saved-1");
        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void save_missingTargetType_throwsBadRequestAndNeverSaves() {
        SaveItemRequest request = new SaveItemRequest(null, "post-1", "post-owner-1");

        assertThatThrownBy(() -> savedItemService.save(request))
                .isInstanceOf(BadRequestException.class);

        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void save_missingTargetId_throwsBadRequest() {
        SaveItemRequest request = new SaveItemRequest(TargetType.POST, null, "post-owner-1");

        assertThatThrownBy(() -> savedItemService.save(request))
                .isInstanceOf(BadRequestException.class);

        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void unsave_delegatesToRepositoryDeleteForCurrentUser() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));

        savedItemService.unsave(TargetType.POST, "post-1");

        verify(savedItemRepository).deleteByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1");
    }

    @Test
    void getMine_present_returnsIt() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        SavedItem existing = existingSavedItem("saved-1", "user-1");
        when(savedItemRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.of(existing));

        Optional<SavedItem> result = savedItemService.getMine(TargetType.POST, "post-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("saved-1");
    }

    @Test
    void getMine_absent_returnsEmpty() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        when(savedItemRepository.findByTargetTypeAndTargetIdAndUserId(TargetType.POST, "post-1", "user-1"))
                .thenReturn(Optional.empty());

        Optional<SavedItem> result = savedItemService.getMine(TargetType.POST, "post-1");

        assertThat(result).isEmpty();
    }

    @Test
    void listMine_delegatesToRepositoryForCurrentUser() {
        CurrentUserContext.setForTests("user-1", List.of("USER"));
        SavedItem existing = existingSavedItem("saved-1", "user-1");
        Pageable pageable = PageRequest.of(0, 20);
        Page<SavedItem> page = new PageImpl<>(List.of(existing));
        when(savedItemRepository.findByUserIdOrderByCreatedAtDesc("user-1", pageable)).thenReturn(page);

        Page<SavedItem> result = savedItemService.listMine(pageable);

        assertThat(result.getContent()).containsExactly(existing);
    }
}
