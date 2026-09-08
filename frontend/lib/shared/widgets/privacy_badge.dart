import 'package:flutter/material.dart';

import '../../core/models/enums.dart';

class PrivacyBadge extends StatelessWidget {
  final Privacy privacy;

  const PrivacyBadge({super.key, required this.privacy});

  IconData get _icon {
    switch (privacy) {
      case Privacy.public:
        return Icons.public;
      case Privacy.friends:
        return Icons.people;
      case Privacy.custom:
        return Icons.tune;
      case Privacy.private:
        return Icons.lock;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: privacy.label,
      child: Icon(_icon, size: 14, color: Colors.grey.shade600),
    );
  }
}
