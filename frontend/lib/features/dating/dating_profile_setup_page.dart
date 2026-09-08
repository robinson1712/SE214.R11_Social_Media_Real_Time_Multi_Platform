import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/dating.dart';
import '../../core/models/enums.dart';
import 'dating_provider.dart';
import 'dating_repository.dart';

class DatingProfileSetupPage extends ConsumerStatefulWidget {
  const DatingProfileSetupPage({super.key});

  @override
  ConsumerState<DatingProfileSetupPage> createState() => _DatingProfileSetupPageState();
}

class _DatingProfileSetupPageState extends ConsumerState<DatingProfileSetupPage> {
  final _bioController = TextEditingController();
  final _interestsController = TextEditingController();
  Gender _gender = Gender.other;
  GenderPreference _genderPreference = GenderPreference.any;
  DateTime _birthDate = DateTime(2000, 1, 1);
  RangeValues _ageRange = const RangeValues(18, 40);
  bool _loaded = false;
  bool _submitting = false;
  String? _error;

  void _applyProfile(DatingProfile? profile) {
    if (profile == null || _loaded) return;
    _loaded = true;
    _bioController.text = profile.bio ?? '';
    _interestsController.text = profile.interests.join(', ');
    _gender = profile.gender;
    _genderPreference = profile.genderPreference;
    _birthDate = profile.birthDate;
    _ageRange = RangeValues(
        profile.minAgePreference.toDouble(), profile.maxAgePreference.toDouble());
  }

  @override
  void dispose() {
    _bioController.dispose();
    _interestsController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(datingRepositoryProvider).upsertProfile(UpsertProfileRequest(
            gender: _gender,
            birthDate: _birthDate,
            bio: _bioController.text.trim().isEmpty ? null : _bioController.text.trim(),
            interests: _interestsController.text
                .split(',')
                .map((e) => e.trim())
                .where((e) => e.isNotEmpty)
                .toList(),
            minAgePreference: _ageRange.start.round(),
            maxAgePreference: _ageRange.end.round(),
            genderPreference: _genderPreference,
          ));
      ref.invalidate(myDatingProfileProvider);
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final profileAsync = ref.watch(myDatingProfileProvider);
    profileAsync.whenData(_applyProfile);

    return Scaffold(
      appBar: AppBar(title: const Text('Hồ sơ hẹn hò')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              DropdownButtonFormField<Gender>(
                value: _gender,
                decoration: const InputDecoration(labelText: 'Giới tính của bạn'),
                items: const [
                  DropdownMenuItem(value: Gender.male, child: Text('Nam')),
                  DropdownMenuItem(value: Gender.female, child: Text('Nữ')),
                  DropdownMenuItem(value: Gender.other, child: Text('Khác')),
                ],
                onChanged: (v) => setState(() => _gender = v!),
              ),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text('Ngày sinh: ${_birthDate.day}/${_birthDate.month}/${_birthDate.year}'),
                trailing: const Icon(Icons.calendar_today, size: 18),
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: _birthDate,
                    firstDate: DateTime(1940),
                    lastDate: DateTime.now().subtract(const Duration(days: 365 * 18)),
                  );
                  if (picked != null) setState(() => _birthDate = picked);
                },
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _bioController,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Giới thiệu bản thân'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _interestsController,
                decoration: const InputDecoration(
                    labelText: 'Sở thích (cách nhau bằng dấu phẩy)', hintText: 'du lịch, âm nhạc, ...'),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<GenderPreference>(
                value: _genderPreference,
                decoration: const InputDecoration(labelText: 'Tôi muốn tìm'),
                items: GenderPreference.values
                    .map((p) => DropdownMenuItem(value: p, child: Text(p.label)))
                    .toList(),
                onChanged: (v) => setState(() => _genderPreference = v!),
              ),
              const SizedBox(height: 12),
              Text('Độ tuổi mong muốn: ${_ageRange.start.round()} - ${_ageRange.end.round()}'),
              RangeSlider(
                values: _ageRange,
                min: 18,
                max: 80,
                divisions: 62,
                labels: RangeLabels('${_ageRange.start.round()}', '${_ageRange.end.round()}'),
                onChanged: (v) => setState(() => _ageRange = v),
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
                    : const Text('Lưu hồ sơ'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
