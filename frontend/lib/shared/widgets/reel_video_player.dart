import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:video_player/video_player.dart';

import '../../features/reels/reel_provider.dart';

/// Autoplaying, looping, tap-to-mute video player for a single reel. Mute
/// is shared via [reelMutedProvider] rather than owned as private State —
/// the action column next to this player renders the actual mute icon (see
/// reels_feed_page.dart), so both need to agree on the same on/off value;
/// this widget still supports tap-anywhere-on-the-video as a shortcut.
class ReelVideoPlayer extends ConsumerStatefulWidget {
  final String reelId;
  final String videoUrl;
  final bool autoplay;

  const ReelVideoPlayer({super.key, required this.reelId, required this.videoUrl, this.autoplay = true});

  @override
  ConsumerState<ReelVideoPlayer> createState() => _ReelVideoPlayerState();
}

class _ReelVideoPlayerState extends ConsumerState<ReelVideoPlayer> {
  late final VideoPlayerController _controller;
  bool _ready = false;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.networkUrl(Uri.parse(widget.videoUrl))
      ..setLooping(true)
      ..setVolume(0);
    _initialize();
  }

  // A broken/unsupported video source makes initialize() (and play())
  // reject rather than throw synchronously — left uncaught, that becomes an
  // unhandled Future error that crashes the whole app, not just this tile.
  Future<void> _initialize() async {
    try {
      await _controller.initialize();
      if (!mounted) return;
      setState(() => _ready = true);
      if (widget.autoplay) {
        await _controller.play();
      }
    } catch (_) {
      if (!mounted) return;
      setState(() => _failed = true);
    }
  }

  @override
  void didUpdateWidget(covariant ReelVideoPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_failed || !_ready) return;
    if (!widget.autoplay) {
      _controller.pause();
    } else {
      _controller.play();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggleMute() {
    final muted = !ref.read(reelMutedProvider(widget.reelId));
    ref.read(reelMutedProvider(widget.reelId).notifier).state = muted;
    _controller.setVolume(muted ? 0 : 1);
  }

  @override
  Widget build(BuildContext context) {
    if (_failed) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, color: Colors.white54, size: 40),
            SizedBox(height: 8),
            Text('Không phát được video này', style: TextStyle(color: Colors.white54)),
          ],
        ),
      );
    }
    if (!_ready) {
      return const Center(child: CircularProgressIndicator(color: Colors.white));
    }
    // Applies the shared mute state to this controller whenever it changes
    // (including from the action column's own mute button, not just the
    // tap-on-video shortcut below).
    ref.listen<bool>(reelMutedProvider(widget.reelId), (previous, muted) {
      _controller.setVolume(muted ? 0 : 1);
    });
    return GestureDetector(
      onTap: _toggleMute,
      child: FittedBox(
        fit: BoxFit.cover,
        child: SizedBox(
          width: _controller.value.size.width,
          height: _controller.value.size.height,
          child: VideoPlayer(_controller),
        ),
      ),
    );
  }
}
