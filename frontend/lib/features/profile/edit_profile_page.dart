import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/api/media_repository.dart';
import '../../core/models/enums.dart';
import '../../core/models/user_profile.dart';
import '../../shared/widgets/avatar.dart';
import 'profile_provider.dart';
import 'profile_repository.dart';

class EditProfilePage extends ConsumerStatefulWidget {
  final UserProfile profile;

  const EditProfilePage({super.key, required this.profile});

  @override
  ConsumerState<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends ConsumerState<EditProfilePage> {
  late final TextEditingController _fullNameController;
  late final TextEditingController _bioController;
  late final TextEditingController _locationController;
  late final TextEditingController _workplaceController;
  Gender? _gender;
  DateTime? _dob;
  String? _avatarUrl;
  String? _coverUrl;
  bool _saving = false;
  bool _uploadingAvatar = false;
  bool _uploadingCover = false;
  late bool _readReceiptsEnabled;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fullNameController = TextEditingController(text: widget.profile.fullName);
    _bioController = TextEditingController(text: widget.profile.bio ?? '');
    _locationController = TextEditingController(text: widget.profile.location ?? '');
    _workplaceController = TextEditingController(text: widget.profile.workplace ?? '');
    // "Khác" isn't offered in this form's dropdown below (only Nam/Nữ) —
    // normalize it to unset rather than leaving a value the dropdown has no
    // matching item for, which throws ("exactly one item with value").
    _gender = widget.profile.gender == Gender.other ? null : widget.profile.gender;
    _dob = widget.profile.dob;
    _avatarUrl = widget.profile.avatarUrl;
    _coverUrl = widget.profile.coverUrl;
    _readReceiptsEnabled = widget.profile.readReceiptsEnabled;
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _bioController.dispose();
    _locationController.dispose();
    _workplaceController.dispose();
    super.dispose();
  }

  Future<void> _pickAndUpload({required bool isAvatar}) async {
    final picker = ImagePicker();
    final file = await picker.pickImage(source: ImageSource.gallery);
    if (file == null) return;
    setState(() {
      if (isAvatar) {
        _uploadingAvatar = true;
      } else {
        _uploadingCover = true;
      }
    });
    try {
      final bytes = await file.readAsBytes();
      final url = await ref.read(mediaRepositoryProvider).uploadBytes(
            bytes: bytes,
            filename: file.name,
            purpose: isAvatar ? MediaPurpose.avatar : MediaPurpose.cover,
          );
      final repo = ref.read(profileRepositoryProvider);
      if (isAvatar) {
        await repo.updateAvatar(url);
      } else {
        await repo.updateCover(url);
      }
      setState(() {
        if (isAvatar) {
          _avatarUrl = url;
        } else {
          _coverUrl = url;
        }
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) {
        setState(() {
          _uploadingAvatar = false;
          _uploadingCover = false;
        });
      }
    }
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(profileRepositoryProvider).updateMe(
            UpdateProfileRequest(
              fullName: _fullNameController.text.trim(),
              bio: _bioController.text.trim(),
              dob: _dob,
              gender: _gender,
              location: _locationController.text.trim(),
              workplace: _workplaceController.text.trim(),
              readReceiptsEnabled: _readReceiptsEnabled,
            ),
          );
      ref.invalidate(viewedProfileProvider(widget.profile.id));
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chỉnh sửa trang cá nhân'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Lưu'),
          ),
        ],
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Stack(
                alignment: Alignment.center,
                children: [
                  GestureDetector(
                    onTap: _uploadingCover ? null : () => _pickAndUpload(isAvatar: false),
                    child: Container(
                      height: 140,
                      decoration: BoxDecoration(
                        color: Colors.grey.shade200,
                        borderRadius: BorderRadius.circular(8),
                        image: _coverUrl != null
                            ? DecorationImage(image: NetworkImage(_coverUrl!), fit: BoxFit.cover)
                            : null,
                      ),
                      child: _uploadingCover
                          ? const Center(child: CircularProgressIndicator())
                          : (_coverUrl == null
                              ? const Center(child: Icon(Icons.add_photo_alternate))
                              : null),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Center(
                child: GestureDetector(
                  onTap: _uploadingAvatar ? null : () => _pickAndUpload(isAvatar: true),
                  child: Stack(
                    children: [
                      Avatar(
                          url: _avatarUrl,
                          name: _fullNameController.text,
                          radius: 44,
                          gender: _gender),
                      if (_uploadingAvatar)
                        const Positioned.fill(
                            child: Center(child: CircularProgressIndicator())),
                      const Positioned(
                        right: 0,
                        bottom: 0,
                        child: CircleAvatar(radius: 12, child: Icon(Icons.camera_alt, size: 14)),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
              TextField(
                controller: _fullNameController,
                decoration: const InputDecoration(labelText: 'Họ và tên'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _bioController,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Giới thiệu bản thân'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _locationController,
                decoration: const InputDecoration(labelText: 'Nơi ở'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _workplaceController,
                decoration: const InputDecoration(
                    labelText: 'Công việc / Học vấn',
                    hintText: 'VD: Làm việc tại ABC hoặc Học tại XYZ'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<Gender>(
                value: _gender,
                decoration: const InputDecoration(labelText: 'Giới tính'),
                items: const [
                  DropdownMenuItem(value: Gender.male, child: Text('Nam')),
                  DropdownMenuItem(value: Gender.female, child: Text('Nữ')),
                ],
                onChanged: (v) => setState(() => _gender = v),
              ),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(_dob == null
                    ? 'Ngày sinh: chưa đặt'
                    : 'Ngày sinh: ${_dob!.day}/${_dob!.month}/${_dob!.year}'),
                trailing: const Icon(Icons.calendar_today, size: 18),
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: _dob ?? DateTime(2000, 1, 1),
                    firstDate: DateTime(1900),
                    lastDate: DateTime.now(),
                  );
                  if (picked != null) setState(() => _dob = picked);
                },
              ),
              const SizedBox(height: 12),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Đã xem (read receipts)'),
                subtitle: const Text(
                  'Tắt để ẩn "Đã xem" của bạn trong Trò chuyện — khi tắt, bạn cũng sẽ không '
                  'thấy trạng thái đã xem của người khác (áp dụng 2 chiều, giống Facebook).',
                ),
                value: _readReceiptsEnabled,
                onChanged: (v) => setState(() => _readReceiptsEnabled = v),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('Lưu thay đổi'),
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
