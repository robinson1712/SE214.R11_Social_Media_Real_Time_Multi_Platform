package com.socialapp.moderation.repository;

import com.socialapp.moderation.entity.Report;
import com.socialapp.moderation.entity.ReportStatus;
import com.socialapp.moderation.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {

    Page<Report> findByReporterIdOrderByCreatedAtDesc(String reporterId, Pageable pageable);

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType, Pageable pageable);

    Page<Report> findByStatusAndTargetTypeOrderByCreatedAtDesc(ReportStatus status, ReportTargetType targetType, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
