import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/moderation.dart';
import '../../core/models/page_response.dart';
import 'moderation_repository.dart';

final allReportsProvider = FutureProvider.family<PageResponse<Report>, ({String? status, String? targetType})>(
  (ref, filter) {
    return ref.read(moderationRepositoryProvider).allReports(
          status: filter.status,
          targetType: filter.targetType,
          size: 50,
        );
  },
);

class ModerationActions {
  final Ref _ref;

  ModerationActions(this._ref);

  ModerationRepository get _repository => _ref.read(moderationRepositoryProvider);

  Future<Report> createReport(CreateReportRequest request) => _repository.createReport(request);

  Future<void> resolve(String id, ResolveReportRequest request) async {
    await _repository.resolve(id, request);
    _ref.invalidate(allReportsProvider);
  }
}

final moderationActionsProvider = Provider((ref) => ModerationActions(ref));
