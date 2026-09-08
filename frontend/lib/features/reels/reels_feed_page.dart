import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/widgets/reel_video_player.dart';
import '../../shared/widgets/user_inline.dart';
import '../comments/comment_section.dart';
import '../posts/share_dialog.dart';
import '../reactions/reaction_picker.dart';
import '../reactions/reaction_provider.dart';
import '../saved/saved_provider.dart';
import '../../core/models/enums.dart';
import '../../core/models/reel.dart';
import 'create_reel_dialog.dart';
import 'reel_provider.dart';

class ReelsFeedPage extends ConsumerStatefulWidget {
  const ReelsFeedPage({super.key});

  @override
  ConsumerState<ReelsFeedPage> createState() => _ReelsFeedPageState();
}

class _ReelsFeedPageState extends ConsumerState<ReelsFeedPage> {
  final _pageController = PageController();
  int _current = 0;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(reelFeedProvider);

    // No AppBar — AppShell already owns the app's one persistent top bar,
    // and this page is meant to be an immersive full-bleed video view (like
    // Reels/TikTok) where a second bar just eats into that. The "+" create
    // button that used to live in the AppBar's actions is real
    // functionality, so it moves to a floating button over the video
    // instead of disappearing with the bar.
    return Scaffold(
      backgroundColor: Colors.black,
      floatingActionButton: FloatingActionButton(
        backgroundColor: Colors.white24,
        foregroundColor: Colors.white,
        onPressed: () => showCreateReelDialog(context),
        child: const Icon(Icons.add),
      ),
      body: state.loading
          ? const Center(child: CircularProgressIndicator())
          : state.reels.isEmpty
              ? const Center(
                  child: Text('Chưa có reel nào', style: TextStyle(color: Colors.white70)))
              : Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: PageView.builder(
                      controller: _pageController,
                      scrollDirection: Axis.vertical,
                      itemCount: state.reels.length,
                      onPageChanged: (index) {
                        setState(() => _current = index);
                        ref.read(reelActionsProvider).view(state.reels[index].id);
                        if (index > state.reels.length - 3) {
                          ref.read(reelFeedProvider.notifier).loadMore();
                        }
                      },
                      itemBuilder: (context, index) {
                        final reel = state.reels[index];
                        return Stack(
                          fit: StackFit.expand,
                          children: [
                            ReelVideoPlayer(
                              reelId: reel.id,
                              videoUrl: reel.videoUrl,
                              autoplay: index == _current,
                            ),
                            Positioned(
                              left: 12,
                              right: 60,
                              bottom: 24,
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  UserInline(
                                    userId: reel.authorId,
                                    avatarRadius: 16,
                                    nameStyle: const TextStyle(
                                        color: Colors.white, fontWeight: FontWeight.bold),
                                  ),
                                  if (reel.caption != null && reel.caption!.isNotEmpty)
                                    Padding(
                                      padding: const EdgeInsets.only(top: 6),
                                      child: Text(reel.caption!,
                                          style: const TextStyle(color: Colors.white)),
                                    ),
                                ],
                              ),
                            ),
                            Positioned(
                              right: 8,
                              bottom: 24,
                              child: _ReelActionColumn(reel: reel),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                ),
    );
  }
}

/// TikTok-style single vertical action column on the right of each reel —
/// every control (like, comment, share, save, view count, mute) lives in
/// this one Column now; they used to be split across two separately
/// positioned overlays (this column plus ReelVideoPlayer's own mute icon),
/// which visually collided at nearly the same corner.
class _ReelActionColumn extends ConsumerWidget {
  final Reel reel;

  const _ReelActionColumn({required this.reel});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reactionKey = ReactionKey(
      targetType: TargetType.reel,
      targetId: reel.id,
      targetOwnerId: reel.authorId,
    );
    final reactionTotal = ref.watch(reactionProvider(reactionKey)).summary.total;
    final saveKey = SaveKey(targetType: TargetType.reel, targetId: reel.id);
    final saved = ref.watch(savedProvider(saveKey));
    final muted = ref.watch(reelMutedProvider(reel.id));

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ReactionPicker(
            reactionKey: reactionKey,
            showLabel: false,
            unlikedTint: Colors.white,
          ),
          if (reactionTotal > 0) _CountLabel(reactionTotal),
          const SizedBox(height: 18),
          _ActionIcon(
            icon: Icons.mode_comment_outlined,
            onTap: () => showModalBottomSheet(
              context: context,
              isScrollControlled: true,
              builder: (context) => DraggableScrollableSheet(
                initialChildSize: 0.6,
                expand: false,
                builder: (context, scrollController) => Padding(
                  padding: const EdgeInsets.all(12),
                  child: SingleChildScrollView(
                    controller: scrollController,
                    child: CommentSection(
                      targetType: TargetType.reel,
                      targetId: reel.id,
                      targetOwnerId: reel.authorId,
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (reel.commentCount > 0) _CountLabel(reel.commentCount),
          const SizedBox(height: 18),
          _ActionIcon(
            icon: Icons.share_outlined,
            onTap: () => showReelShareDialog(context, reel.id),
          ),
          if (reel.shareCount > 0) _CountLabel(reel.shareCount),
          const SizedBox(height: 18),
          _ActionIcon(
            icon: saved ? Icons.bookmark : Icons.bookmark_border,
            onTap: () => ref.read(savedProvider(saveKey).notifier).toggle(targetOwnerId: reel.authorId),
          ),
          const SizedBox(height: 18),
          const Icon(Icons.remove_red_eye, color: Colors.white70, size: 24),
          _CountLabel(reel.viewCount),
          const SizedBox(height: 18),
          _ActionIcon(
            icon: muted ? Icons.volume_off : Icons.volume_up,
            onTap: () => ref.read(reelMutedProvider(reel.id).notifier).state = !muted,
          ),
        ],
      ),
    );
  }
}

class _ActionIcon extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _ActionIcon({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(icon, color: Colors.white, size: 26),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
      onPressed: onTap,
    );
  }
}

class _CountLabel extends StatelessWidget {
  final num count;

  const _CountLabel(this.count);

  @override
  Widget build(BuildContext context) {
    return Text('$count', style: const TextStyle(color: Colors.white70, fontSize: 12));
  }
}
