import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import '../../core/models/moderation.dart';
import 'moderation_provider.dart';

/// Reusable "report this content" dialog — usable from a post, comment,
/// reel, story, group, fanpage, or user profile context.
Future<void> showReportDialog(
  BuildContext context, {
  required ReportTargetType targetType,
  required String targetId,
}) {
  return showDialog(
    context: context,
    builder: (_) => _ReportDialog(targetType: targetType, targetId: targetId),
  );
}

class _ReportDialog extends ConsumerStatefulWidget {
  final ReportTargetType targetType;
  final String targetId;

  const _ReportDialog({required this.targetType, required this.targetId});

  @override
  ConsumerState<_ReportDialog> createState() => _ReportDialogState();
}

class _ReportDialogState extends ConsumerState<_ReportDialog> {
  final _descController = TextEditingController();
  ReportReason _reason = ReportReason.spam;
  bool _submitting = false;
  String? _error;
  bool _submitted = false;

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(moderationActionsProvider).createReport(CreateReportRequest(
            targetType: widget.targetType,
            targetId: widget.targetId,
            reason: _reason,
            description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
          ));
      if (mounted) setState(() => _submitted = true);
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_submitted) {
      return AlertDialog(
        title: const Text('Đã gửi báo cáo'),
        content: const Text('Cảm ơn bạn đã báo cáo. Đội ngũ quản trị sẽ xem xét sớm.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Đóng')),
        ],
      );
    }

    return AlertDialog(
      title: const Text('Báo cáo nội dung'),
      content: SizedBox(
        width: 380,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            DropdownButtonFormField<ReportReason>(
              value: _reason,
              decoration: const InputDecoration(labelText: 'Lý do'),
              items: ReportReason.values
                  .map((r) => DropdownMenuItem(value: r, child: Text(r.label)))
                  .toList(),
              onChanged: (v) => setState(() => _reason = v!),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descController,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Mô tả thêm (tuỳ chọn)'),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Huỷ')),
        ElevatedButton(
          onPressed: _submitting ? null : _submit,
          child: _submitting
              ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Gửi báo cáo'),
        ),
      ],
    );
  }
}
