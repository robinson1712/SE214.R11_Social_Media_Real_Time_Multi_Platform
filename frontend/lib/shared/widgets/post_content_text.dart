import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/profile/user_lookup_provider.dart';

/// Renders a post's text content with `@Name` mentions turned into bold,
/// tappable links to that user's profile — mirrors how the composer
/// inserted them (`@${user.fullName} `), so matching is just a substring
/// scan against each tagged user's current display name. Falls back to
/// plain text when there's nothing to tag or a name hasn't resolved yet.
class PostContentText extends ConsumerWidget {
  final String content;
  final List<String> taggedUserIds;
  final TextStyle? style;

  const PostContentText({
    super.key,
    required this.content,
    this.taggedUserIds = const [],
    this.style,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (taggedUserIds.isEmpty) return Text(content, style: style);

    final mentionsByNeedle = <String, String>{};
    for (final id in taggedUserIds) {
      final name = ref.watch(userLookupProvider(id)).value?.fullName;
      if (name != null && name.isNotEmpty) mentionsByNeedle['@$name'] = id;
    }
    if (mentionsByNeedle.isEmpty) return Text(content, style: style);

    // Longest names first so e.g. "@Anna Nguyen" isn't pre-empted by a
    // shorter "@Anna" belonging to someone else tagged in the same post.
    final needles = mentionsByNeedle.keys.toList()
      ..sort((a, b) => b.length.compareTo(a.length));

    final baseStyle = DefaultTextStyle.of(context).style.merge(style);
    final mentionStyle = baseStyle.copyWith(
      color: Theme.of(context).primaryColor,
      fontWeight: FontWeight.bold,
    );

    final spans = <InlineSpan>[];
    final buffer = StringBuffer();
    var i = 0;
    while (i < content.length) {
      String? matchedNeedle;
      for (final needle in needles) {
        if (content.startsWith(needle, i)) {
          matchedNeedle = needle;
          break;
        }
      }
      if (matchedNeedle != null) {
        if (buffer.isNotEmpty) {
          spans.add(TextSpan(text: buffer.toString()));
          buffer.clear();
        }
        final userId = mentionsByNeedle[matchedNeedle]!;
        spans.add(WidgetSpan(
          alignment: PlaceholderAlignment.baseline,
          baseline: TextBaseline.alphabetic,
          child: GestureDetector(
            onTap: () => context.push('/profile/$userId'),
            child: Text(matchedNeedle, style: mentionStyle),
          ),
        ));
        i += matchedNeedle.length;
      } else {
        buffer.write(content[i]);
        i++;
      }
    }
    if (buffer.isNotEmpty) spans.add(TextSpan(text: buffer.toString()));

    return Text.rich(TextSpan(style: baseStyle, children: spans));
  }
}
