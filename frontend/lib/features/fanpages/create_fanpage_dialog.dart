import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/fanpage.dart';
import 'fanpage_provider.dart';

Future<void> showCreateFanpageDialog(BuildContext context) {
  return showDialog(context: context, builder: (_) => const _CreateFanpageDialog());
}

class _CreateFanpageDialog extends ConsumerStatefulWidget {
  const _CreateFanpageDialog();

  @override
  ConsumerState<_CreateFanpageDialog> createState() => _CreateFanpageDialogState();
}

class _CreateFanpageDialogState extends ConsumerState<_CreateFanpageDialog> {
  final _nameController = TextEditingController();
  final _categoryController = TextEditingController();
  final _descController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    _categoryController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_nameController.text.trim().isEmpty) {
      setState(() => _error = 'Nhập tên trang');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(fanpageActionsProvider).create(CreateFanpageRequest(
            name: _nameController.text.trim(),
            category: _categoryController.text.trim().isEmpty ? null : _categoryController.text.trim(),
            description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
          ));
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 460),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Tạo trang (Fanpage)', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(labelText: 'Tên trang'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _categoryController,
                decoration: const InputDecoration(labelText: 'Danh mục (tuỳ chọn)'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _descController,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Mô tả (tuỳ chọn)'),
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _submitting ? null : _submit,
                child: _submitting
                    ? const SizedBox(
                        height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Tạo trang'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
