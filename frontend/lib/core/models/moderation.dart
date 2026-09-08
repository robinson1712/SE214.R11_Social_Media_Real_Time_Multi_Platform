import 'enums.dart';

/// Mirrors moderation-service's `Report` entity.
class Report {
  final String id;
  final String reporterId;
  final ReportTargetType targetType;
  final String targetId;
  final ReportReason reason;
  final String? description;
  final ReportStatus status;
  final String? reviewedBy;
  final DateTime? reviewedAt;
  final String? resolutionNote;
  final DateTime? createdAt;

  const Report({
    required this.id,
    required this.reporterId,
    required this.targetType,
    required this.targetId,
    required this.reason,
    this.description,
    this.status = ReportStatus.pending,
    this.reviewedBy,
    this.reviewedAt,
    this.resolutionNote,
    this.createdAt,
  });

  factory Report.fromJson(Map<String, dynamic> json) => Report(
        id: json['id'] as String,
        reporterId: json['reporterId'] as String,
        targetType: ReportTargetTypeJson.fromJson(json['targetType'] as String?),
        targetId: json['targetId'] as String,
        reason: ReportReasonJson.fromJson(json['reason'] as String?),
        description: json['description'] as String?,
        status: ReportStatusJson.fromJson(json['status'] as String?),
        reviewedBy: json['reviewedBy'] as String?,
        reviewedAt: json['reviewedAt'] == null
            ? null
            : DateTime.tryParse(json['reviewedAt'] as String),
        resolutionNote: json['resolutionNote'] as String?,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class CreateReportRequest {
  final ReportTargetType targetType;
  final String targetId;
  final ReportReason reason;
  final String? description;

  const CreateReportRequest({
    required this.targetType,
    required this.targetId,
    required this.reason,
    this.description,
  });

  Map<String, dynamic> toJson() => {
        'targetType': targetType.toJson(),
        'targetId': targetId,
        'reason': reason.toJson(),
        'description': description,
      };
}

class ResolveReportRequest {
  final ModerationAction action;
  final String? note;

  const ResolveReportRequest({required this.action, this.note});

  Map<String, dynamic> toJson() => {
        'action': action.toJson(),
        'note': note,
      };
}
