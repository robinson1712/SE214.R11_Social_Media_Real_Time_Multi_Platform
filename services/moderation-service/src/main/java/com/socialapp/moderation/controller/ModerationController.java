package com.socialapp.moderation.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.moderation.dto.CreateReportRequest;
import com.socialapp.moderation.dto.ResolveReportRequest;
import com.socialapp.moderation.entity.Report;
import com.socialapp.moderation.entity.ReportStatus;
import com.socialapp.moderation.entity.ReportTargetType;
import com.socialapp.moderation.service.ModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<Report>> createReport(@Valid @RequestBody CreateReportRequest request) {
        Report report = moderationService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Report submitted", report));
    }

    @GetMapping("/reports/mine")
    public ResponseEntity<ApiResponse<PageResponse<Report>>> myReports(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(moderationService.getMyReports(pageable))));
    }

    /** Admin-only — see ModerationService.requireAdmin(). */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<Report>>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(moderationService.listReports(status, targetType, pageable))));
    }

    /** Admin-only — see ModerationService.requireAdmin(). */
    @GetMapping("/reports/{id}")
    public ResponseEntity<ApiResponse<Report>> getReport(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getReport(id)));
    }

    /** Admin-only — see ModerationService.requireAdmin(). DISMISS just closes the report; REMOVE_CONTENT also publishes ContentRemovedEvent. */
    @PutMapping("/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<Report>> resolveReport(@PathVariable String id, @Valid @RequestBody ResolveReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Report resolved", moderationService.resolveReport(id, request)));
    }
}
