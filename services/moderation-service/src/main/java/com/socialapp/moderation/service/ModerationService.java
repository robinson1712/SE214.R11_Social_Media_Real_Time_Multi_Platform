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
import com.socialapp.moderation.entity.ReportStatus;
import com.socialapp.moderation.entity.ReportTargetType;
import com.socialapp.moderation.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final ReportRepository reportRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Report createReport(CreateReportRequest request) {
        String reporterId = requireUserId();

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(request.reason())
                .description(request.description())
                .status(ReportStatus.PENDING)
                .build();

        return reportRepository.save(report);
    }

    public Page<Report> getMyReports(Pageable pageable) {
        String reporterId = requireUserId();
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId, pageable);
    }

    public Page<Report> listReports(ReportStatus status, ReportTargetType targetType, Pageable pageable) {
        requireAdmin();
        if (status != null && targetType != null) {
            return reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(status, targetType, pageable);
        }
        if (status != null) {
            return reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        if (targetType != null) {
            return reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType, pageable);
        }
        return reportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Report getReport(String id) {
        requireAdmin();
        return getReportOrThrow(id);
    }

    public Report resolveReport(String id, ResolveReportRequest request) {
        String adminId = requireAdmin();
        Report report = getReportOrThrow(id);

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ConflictException("Report has already been resolved");
        }

        report.setReviewedBy(adminId);
        report.setReviewedAt(Instant.now());
        report.setResolutionNote(request.note());

        if (request.action() == ResolveReportRequest.ModerationAction.REMOVE_CONTENT) {
            report.setStatus(ReportStatus.ACTION_TAKEN);
            Report saved = reportRepository.save(report);

            ContentRemovedEvent event = new ContentRemovedEvent(
                    saved.getTargetType().name(), saved.getTargetId(), saved.getId(),
                    saved.getReason().name(), Instant.now());
            kafkaTemplate.send(KafkaTopics.CONTENT_REMOVED, saved.getTargetId(), event);

            return saved;
        }

        report.setStatus(ReportStatus.DISMISSED);
        return reportRepository.save(report);
    }

    private Report getReportOrThrow(String id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    private String requireUserId() {
        String userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }

    /** Returns the caller's id once confirmed to hold ADMIN — convenient for stamping reviewedBy. */
    private String requireAdmin() {
        String userId = requireUserId();
        var roles = CurrentUserContext.getRoles();
        if (roles == null || !roles.contains(ADMIN_ROLE)) {
            throw new ForbiddenException("Admin access required");
        }
        return userId;
    }
}
