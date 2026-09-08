import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:video_player/video_player.dart';

import '../../core/models/enums.dart';
import '../../core/models/story.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import '../chat/chat_provider.dart';
import '../reactions/reaction_provider.dart';
import 'story_provider.dart';

const Duration _kImageStoryDuration = Duration(seconds: 5);
const Size _kViewerSize = Size(420, 720);

Future<void> showStoryViewer(BuildContext context, List<Story> stories, int startIndex) {
  return showDialog(
    context: context,
    barrierColor: Colors.black,
    builder: (_) => _StoryViewerDialog(stories: stories, startIndex: startIndex),
  );
}

Color _colorFromHex(String hex) {
  final cleaned = hex.replaceFirst('#', '');
  final value = int.tryParse(cleaned, radix: 16) ?? 0xFFFFFF;
  return Color(0xFF000000 | value);
}

class _StoryViewerDialog extends ConsumerStatefulWidget {
  final List<Story> stories;
  final int startIndex;

  const _StoryViewerDialog({required this.stories, required this.startIndex});

  @override
  ConsumerState<_StoryViewerDialog> createState() => _StoryViewerDialogState();
}

class _StoryViewerDialogState extends ConsumerState<_StoryViewerDialog>
    with SingleTickerProviderStateMixin {
  late int _index = widget.startIndex;
  late final AnimationController _progress = AnimationController(
    vsync: this,
    duration: _kImageStoryDuration,
  )..addStatusListener((status) {
      if (status == AnimationStatus.completed) _next();
    });

  VideoPlayerController? _videoController;
  final _replyController = TextEditingController();
  final _replyFocusNode = FocusNode();
  bool _paused = false;
  bool _sendingReply = false;

  @override
  void initState() {
    super.initState();
    _replyFocusNode.addListener(() {
      if (_replyFocusNode.hasFocus) {
        _pause();
      } else {
        _resume();
      }
    });
    _loadStory();
  }

  Future<void> _loadStory() async {
    _markViewed();
    await _setupMedia();
  }

  Future<void> _setupMedia() async {
    final oldController = _videoController;
    _videoController = null;
    final story = widget.stories[_index];
    _progress.duration = _kImageStoryDuration;

    if (story.mediaType == StoryMediaType.video) {
      final controller = VideoPlayerController.networkUrl(Uri.parse(story.mediaUrl));
      bool initialized = false;
      try {
        await controller.initialize();
        initialized = true;
      } catch (_) {
        // Unsupported/broken source — fall back to the default image-style
        // duration and just show a blank/black frame rather than crashing.
      }
      if (!mounted) {
        controller.dispose();
        return;
      }
      if (initialized) {
        controller.setLooping(false);
        final d = controller.value.duration;
        if (d > Duration.zero) _progress.duration = d;
        try {
          await controller.play();
        } catch (_) {
          // Ignore — playback failing after a successful initialize is rare
          // and non-fatal; the story still advances via the progress timer.
        }
        if (!mounted) {
          controller.dispose();
          return;
        }
        setState(() => _videoController = controller);
      } else {
        controller.dispose();
      }
    }
    oldController?.dispose();

    _progress
      ..reset()
      ..forward();
  }

  void _markViewed() {
    ref.read(storyActionsProvider).view(widget.stories[_index].id);
  }

  void _next() {
    if (_index >= widget.stories.length - 1) {
      Navigator.of(context).pop();
      return;
    }
    setState(() => _index++);
    _loadStory();
  }

  void _prev() {
    if (_index <= 0) return;
    setState(() => _index--);
    _loadStory();
  }

  void _pause() {
    if (_paused) return;
    _paused = true;
    _progress.stop();
    _videoController?.pause();
  }

  void _resume() {
    if (!_paused) return;
    _paused = false;
    _progress.forward();
    _videoController?.play();
  }

  Future<void> _sendReply(Story story) async {
    final text = _replyController.text.trim();
    if (text.isEmpty || _sendingReply) return;
    setState(() => _sendingReply = true);
    try {
      final conversation =
          await ref.read(chatProvider.notifier).startConversationWith(story.authorId);
      ref.read(chatProvider.notifier).sendMessage(
            conversation.id,
            text,
            storyReplyId: story.id,
            storyReplyPreviewUrl: story.mediaUrl,
          );
      _replyController.clear();
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Đã gửi trả lời story'), duration: Duration(seconds: 1)));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Không gửi được: $e')));
      }
    } finally {
      if (mounted) setState(() => _sendingReply = false);
    }
  }

  @override
  void dispose() {
    _progress.dispose();
    _videoController?.dispose();
    _replyController.dispose();
    _replyFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final story = widget.stories[_index];
    final myId = ref.watch(currentAccountIdProvider);
    final isMine = myId == story.authorId;

    return Dialog(
      backgroundColor: Colors.black,
      insetPadding: EdgeInsets.zero,
      child: SizedBox(
        width: _kViewerSize.width,
        height: _kViewerSize.height,
        child: Stack(
          children: [
            Positioned.fill(
              child: story.mediaType == StoryMediaType.video && _videoController != null
                  ? FittedBox(
                      fit: BoxFit.cover,
                      child: SizedBox(
                        width: _videoController!.value.size.width,
                        height: _videoController!.value.size.height,
                        child: VideoPlayer(_videoController!),
                      ),
                    )
                  : Image.network(story.mediaUrl, fit: BoxFit.cover),
            ),
            for (final overlay in story.textOverlays)
              Positioned.fill(
                child: Align(
                  alignment: Alignment(overlay.x * 2 - 1, overlay.y * 2 - 1),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      overlay.text,
                      textAlign: TextAlign.center,
                      style: _safeOverlayStyle(overlay),
                    ),
                  ),
                ),
              ),
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Row(
                children: List.generate(
                  widget.stories.length,
                  (i) => Expanded(
                    child: Container(
                      height: 3,
                      margin: const EdgeInsets.symmetric(horizontal: 2, vertical: 8),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(2),
                        child: Stack(
                          children: [
                            Container(color: Colors.white30),
                            AnimatedBuilder(
                              animation: _progress,
                              builder: (context, _) {
                                double fraction;
                                if (i < _index) {
                                  fraction = 1;
                                } else if (i > _index) {
                                  fraction = 0;
                                } else {
                                  fraction = _progress.value;
                                }
                                return FractionallySizedBox(
                                  widthFactor: fraction.clamp(0, 1),
                                  child: Container(color: Colors.white),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              top: 16,
              left: 12,
              right: 12,
              child: Row(
                children: [
                  UserInline(
                    userId: story.authorId,
                    avatarRadius: 16,
                    nameStyle: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  if (isMine)
                    IconButton(
                      icon: const Icon(Icons.delete, color: Colors.white),
                      onPressed: () async {
                        await ref.read(storyActionsProvider).delete(story.id);
                        if (mounted) Navigator.of(context).pop();
                      },
                    ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            if (story.caption != null && story.caption!.isNotEmpty)
              Positioned(
                bottom: 90,
                left: 12,
                right: 12,
                child: Text(story.caption!, style: const TextStyle(color: Colors.white, fontSize: 16)),
              ),
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.black.withValues(alpha: 0.6)],
                  ),
                ),
                child: isMine
                    ? Row(
                        children: [
                          const Icon(Icons.remove_red_eye, color: Colors.white70, size: 16),
                          const SizedBox(width: 4),
                          Text('${story.viewerIds.length} lượt xem',
                              style: const TextStyle(color: Colors.white70, fontSize: 13)),
                        ],
                      )
                    : Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _StoryReactionBar(story: story),
                          const SizedBox(height: 6),
                          Row(
                            children: [
                              Expanded(
                                child: TextField(
                                  controller: _replyController,
                                  focusNode: _replyFocusNode,
                                  style: const TextStyle(color: Colors.white),
                                  decoration: InputDecoration(
                                    hintText: 'Trả lời tin...',
                                    hintStyle: const TextStyle(color: Colors.white60),
                                    filled: true,
                                    fillColor: Colors.white24,
                                    contentPadding:
                                        const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(20),
                                      borderSide: BorderSide.none,
                                    ),
                                  ),
                                  onSubmitted: (_) => _sendReply(story),
                                ),
                              ),
                              IconButton(
                                icon: _sendingReply
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                    : const Icon(Icons.send, color: Colors.white),
                                onPressed: _sendingReply ? null : () => _sendReply(story),
                              ),
                            ],
                          ),
                        ],
                      ),
              ),
            ),
            Positioned.fill(
              child: GestureDetector(
                onLongPressStart: (_) => _pause(),
                onLongPressEnd: (_) => _resume(),
                behavior: HitTestBehavior.translucent,
                child: Row(
                  children: [
                    Expanded(
                        child: GestureDetector(
                            onTap: _prev, behavior: HitTestBehavior.translucent)),
                    Expanded(
                        child: GestureDetector(
                            onTap: _next, behavior: HitTestBehavior.translucent)),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  TextStyle _safeOverlayStyle(TextOverlay o) {
    final color = _colorFromHex(o.color);
    try {
      return GoogleFonts.getFont(o.fontFamily,
          color: color, fontSize: o.fontSize, fontWeight: FontWeight.w600, shadows: const [
        Shadow(color: Colors.black54, blurRadius: 4, offset: Offset(0, 1)),
      ]);
    } catch (_) {
      return TextStyle(color: color, fontSize: o.fontSize, fontWeight: FontWeight.w600, shadows: const [
        Shadow(color: Colors.black54, blurRadius: 4, offset: Offset(0, 1)),
      ]);
    }
  }
}

/// Facebook-style quick-react row shown at the bottom of a story someone
/// else posted — reuses the same generic reaction system as posts/comments,
/// just with targetType STORY.
class _StoryReactionBar extends ConsumerWidget {
  final Story story;

  const _StoryReactionBar({required this.story});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = ReactionKey(
      targetType: TargetType.story,
      targetId: story.id,
      targetOwnerId: story.authorId,
    );
    final state = ref.watch(reactionProvider(key));

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: ReactionType.values.map((type) {
        final selected = state.myReaction == type;
        return GestureDetector(
          onTap: () => ref.read(reactionProvider(key).notifier).react(type),
          child: Container(
            margin: const EdgeInsets.only(right: 6),
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: selected ? Colors.white24 : Colors.transparent,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(type.emoji, style: const TextStyle(fontSize: 20)),
          ),
        );
      }).toList(),
    );
  }
}
