import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import 'reaction_provider.dart';

const _likeIconAsset = 'assets/images/like_icon.png';

/// The "like" glyph, everywhere in the app — the other reaction types
/// (love/haha/wow/sad/angry) still use their emoji (no custom art for
/// those), but "like" is this asset now, not Material's thumb icon or the
/// 👍 emoji. Used for both the picker's own current-reaction display and
/// the emoji row's like option, so there's exactly one place that draws a
/// "like" anywhere in the app.
Widget _reactionGlyph(ReactionType? type, {required double size, required Color unlikedTint}) {
  if (type == null || type == ReactionType.like) {
    final liked = type == ReactionType.like;
    return Image.asset(
      _likeIconAsset,
      width: size,
      color: liked ? null : unlikedTint,
      colorBlendMode: liked ? null : BlendMode.srcIn,
      // The source PNG is 256x246 — displayed here at ~14-28px, a >10x
      // downscale. Image's default filterQuality (low) just point-samples
      // at that ratio instead of averaging neighboring pixels, which is
      // exactly what turns clean anti-aliased edges into jagged ones. High
      // quality actually blends the source down properly.
      filterQuality: FilterQuality.high,
    );
  }
  return Text(type.emoji, style: TextStyle(fontSize: size));
}

/// Facebook-style reaction control: tapping reacts with `like` (or removes
/// it, if already liked), hovering (desktop web) or long-pressing (touch)
/// reveals an emoji row to pick a specific reaction type instead. Used for
/// posts, comments and reels alike — [showLabel] and [unlikedTint] are the
/// only per-surface knobs (reels want an icon with no "Thích" text, on a
/// dark background where the default grey tint would be invisible).
class ReactionPicker extends ConsumerStatefulWidget {
  final ReactionKey reactionKey;
  final bool showLabel;
  final Color unlikedTint;

  const ReactionPicker({
    super.key,
    required this.reactionKey,
    this.showLabel = true,
    this.unlikedTint = const Color(0xFF616161), // Colors.grey.shade700
  });

  @override
  ConsumerState<ReactionPicker> createState() => _ReactionPickerState();
}

class _ReactionPickerState extends ConsumerState<ReactionPicker> {
  OverlayEntry? _entry;
  Timer? _hideTimer;
  final _layerLink = LayerLink();

  void _cancelHide() => _hideTimer?.cancel();

  void _scheduleHide() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(milliseconds: 250), _removePicker);
  }

  void _removePicker() {
    _entry?.remove();
    _entry = null;
  }

  void _showPicker() {
    if (_entry != null) return;
    _entry = OverlayEntry(
      builder: (context) => Positioned(
        width: 260,
        child: CompositedTransformFollower(
          link: _layerLink,
          showWhenUnlinked: false,
          offset: const Offset(0, -52),
          child: MouseRegion(
            onEnter: (_) => _cancelHide(),
            onExit: (_) => _scheduleHide(),
            child: Align(
              alignment: Alignment.topLeft,
              child: Material(
                elevation: 4,
                borderRadius: BorderRadius.circular(24),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: ReactionType.values.map((type) {
                      return InkWell(
                        onTap: () {
                          ref.read(reactionProvider(widget.reactionKey).notifier).react(type);
                          _removePicker();
                        },
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: _reactionGlyph(type, size: 26, unlikedTint: widget.unlikedTint),
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    Overlay.of(context).insert(_entry!);
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _entry?.remove();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(reactionProvider(widget.reactionKey));
    final mine = state.myReaction;

    return CompositedTransformTarget(
      link: _layerLink,
      child: MouseRegion(
        onEnter: (_) {
          _cancelHide();
          _showPicker();
        },
        onExit: (_) => _scheduleHide(),
        child: GestureDetector(
          onLongPress: _showPicker,
          child: widget.showLabel
              ? TextButton.icon(
                  onPressed: () => ref
                      .read(reactionProvider(widget.reactionKey).notifier)
                      .react(mine ?? ReactionType.like),
                  icon: _reactionGlyph(mine, size: 18, unlikedTint: widget.unlikedTint),
                  label: Text(
                    mine == null ? 'Thích' : _label(mine),
                    style: TextStyle(
                      color: mine == null ? widget.unlikedTint : Theme.of(context).primaryColor,
                      fontWeight: mine == null ? FontWeight.normal : FontWeight.bold,
                    ),
                  ),
                )
              : IconButton(
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                  onPressed: () => ref
                      .read(reactionProvider(widget.reactionKey).notifier)
                      .react(mine ?? ReactionType.like),
                  icon: _reactionGlyph(mine, size: 28, unlikedTint: widget.unlikedTint),
                ),
        ),
      ),
    );
  }

  String _label(ReactionType type) {
    switch (type) {
      case ReactionType.like:
        return 'Thích';
      case ReactionType.love:
        return 'Yêu thích';
      case ReactionType.haha:
        return 'Haha';
      case ReactionType.wow:
        return 'Wow';
      case ReactionType.sad:
        return 'Buồn';
      case ReactionType.angry:
        return 'Phẫn nộ';
    }
  }
}

/// Small read-only summary row (emoji stack + total count) shown above the
/// action buttons on a post/comment card.
class ReactionSummaryRow extends ConsumerWidget {
  final ReactionKey reactionKey;

  const ReactionSummaryRow({super.key, required this.reactionKey});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(reactionProvider(reactionKey));
    if (state.summary.total == 0) return const SizedBox.shrink();

    final topTypes = state.summary.counts.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          ...topTypes.take(3).map(
                (e) => Padding(
                  padding: const EdgeInsets.only(right: 2),
                  child: _reactionGlyph(e.key, size: 14, unlikedTint: Colors.grey.shade700),
                ),
              ),
          const SizedBox(width: 4),
          Text('${state.summary.total}',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 13)),
        ],
      ),
    );
  }
}
