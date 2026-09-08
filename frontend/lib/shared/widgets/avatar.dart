import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../core/models/enums.dart';

class Avatar extends StatelessWidget {
  final String? url;
  final String name;
  final double radius;
  final Gender? gender;

  const Avatar({super.key, this.url, required this.name, this.radius = 20, this.gender});

  String get _fallbackAsset => gender == Gender.female
      ? 'assets/images/default_avatar_female.jpg'
      : 'assets/images/default_avatar_male.jpg';

  Widget _fallback() {
    final avatar = ClipOval(
      child: Image.asset(
        _fallbackAsset,
        width: radius * 2,
        height: radius * 2,
        fit: BoxFit.cover,
        filterQuality: FilterQuality.high,
      ),
    );
    return name.trim().isEmpty ? avatar : Tooltip(message: name, child: avatar);
  }

  @override
  Widget build(BuildContext context) {
    if (url == null || url!.isEmpty) {
      return _fallback();
    }

    return ClipOval(
      child: CachedNetworkImage(
        imageUrl: url!,
        width: radius * 2,
        height: radius * 2,
        fit: BoxFit.cover,
        filterQuality: FilterQuality.high,
        placeholder: (context, _) => ClipOval(
          child: Image.asset(
            _fallbackAsset,
            width: radius * 2,
            height: radius * 2,
            fit: BoxFit.cover,
            filterQuality: FilterQuality.high,
          ),
        ),
        errorWidget: (context, _, __) => _fallback(),
      ),
    );
  }
}
