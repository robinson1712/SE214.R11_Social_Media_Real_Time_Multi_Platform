package com.socialapp.moderation.service;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.exception.ConflictException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.moderation.dto.CreateReportRequest;
import com.socialapp.moderation.dto.ResolveReportRequest;
import com.socialapp.moderation.entity.Report;
import com.socialapp.moderation.entity.ReportReason;
import com.socialapp.moderation.entity.ReportStatus;
import com.socialapp.moderation.entity.ReportTargetType;
import com.socialapp.moderation.repository.ReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ModerationService. CurrentUserContext (identity + roles) is
 * set/cleared per test via the test-support seam in common-lib.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        moderationService = new ModerationService(reportRepository, kafkaTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Report pendingReport(String id, String targetId, ReportTargetType targetType) {
        return Report.builder()
                .id(id)
                .reporterId("reporter-1")
                .targetType(targetType)
                .targetId(targetId)
                .reason(ReportReason.HARASSMENT)
                .status(ReportStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createReport_authenticatedUser_savesWithPendingStatus() {
        CurrentUserContext.setForTests("reporter-1", List.of("USER"));
        CreateReportRequest request = new CreateReportRequest(ReportTargetType.POST, "post-1", ReportReason.SPAM, "looks like spam");
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report saved = moderationService.createReport(request);

        assertThat(saved.getReporterId()).isEqualTo("reporter-1");
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.POST);
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void createReport_unauthenticated_throwsUnauthorized() {
        CreateReportRequest request = new CreateReportRequest(ReportTargetType.POST, "post-1", ReportReason.SPAM, null);

        assertThatThrownBy(() -> moderationService.createReport(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void getMyReports_returnsOnlyCallersReports() {
        CurrentUserContext.setForTests("reporter-1", List.of("USER"));
        Pageable pageable = PageRequest.of(0, 20);
        when(reportRepository.findByReporterIdOrderByCreatedAtDesc(eq("reporter-1"), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        moderationService.getMyReports(pageable);

        verify(reportRepository).findByReporterIdOrderByCreatedAtDesc("reporter-1", pageable);
    }

    @Test
    void listReports_nonAdmin_throwsForbidden() {
        CurrentUserContext.setForTests("plain-user", List.of("USER"));

        assertThatThrownBy(() -> moderationService.listReports(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listReports_admin_isAllowedAndFiltersByStatusAndTargetType() {
        CurrentUserContext.setForTests("admin-1", List.of("USER", "ADMIN"));
        Pageable pageable = PageRequest.of(0, 20);
        when(reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(ReportStatus.PENDING, ReportTargetType.POST, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        moderationService.listReports(ReportStatus.PENDING, ReportTargetType.POST, pageable);

        verify(reportRepository).findByStatusAndTargetTypeOrderByCreatedAtDesc(ReportStatus.PENDING, ReportTargetType.POST, pageable);
    }

    @Test
    void getReport_nonAdmin_throwsForbidden() {
        CurrentUserContext.setForTests("plain-user", List.of("USER"));

        assertThatThrownBy(() -> moderationService.getReport("report-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getReport_missing_throwsResourceNotFound() {
        CurrentUserContext.setForTests("admin-1", List.of("USER", "ADMIN"));
        when(reportRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderationService.getReport("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveReport_dismiss_setsStatusAndNeverPublishesEvent() {
        CurrentUserContext.setForTests("admin-1", List.of("USER", "ADMIN"));
        Report report = pendingReport("report-1", "post-1", ReportTargetType.POST);
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report resolved = moderationService.resolveReport("report-1",
                new ResolveReportRequest(ResolveReportRequest.ModerationAction.DISMISS, "not a violation"));

        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(resolved.getReviewedBy()).isEqualTo("admin-1");
        assertThat(resolved.getReviewedAt()).isNotNull();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void resolveReport_removeContent_setsActionTakenAndPublishesContentRemovedEvent() {
        CurrentUserContext.setForTests("admin-1", List.of("USER", "ADMIN"));
        Report report = pendingReport("report-1", "post-1", ReportTargetType.POST);
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report resolved = moderationService.resolveReport("report-1",
                new ResolveReportRequest(ResolveReportRequest.ModerationAction.REMOVE_CONTENT, "confirmed harassment"));

        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.ACTION_TAKEN);

        ArgumentCaptor<ContentRemovedEvent> captor = ArgumentCaptor.forClass(ContentRemovedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CONTENT_REMOVED), eq("post-1"), captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo("POST");
        assertThat(captor.getValue().targetId()).isEqualTo("post-1");
        assertThat(captor.getValue().reportId()).isEqualTo("report-1");
    }

    @Test
    void resolveReport_alreadyResolved_throwsConflict() {
        CurrentUserContext.setForTests("admin-1", List.of("USER", "ADMIN"));
        Report report = pendingReport("report-1", "post-1", ReportTargetType.POST);
        report.setStatus(ReportStatus.DISMISSED);
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> moderationService.resolveReport("report-1",
                new ResolveReportRequest(ResolveReportRequest.ModerationAction.DISMISS, null)))
                .isInstanceOf(ConflictException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void resolveReport_nonAdmin_throwsForbidden() {
        CurrentUserContext.setForTests("plain-user", List.of("USER"));

        assertThatThrownBy(() -> moderationService.resolveReport("report-1",
                new ResolveReportRequest(ResolveReportRequest.ModerationAction.DISMISS, null)))
                .isInstanceOf(ForbiddenException.class);
    }
}
