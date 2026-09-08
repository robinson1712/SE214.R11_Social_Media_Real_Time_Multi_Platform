import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'search_models.dart';
import 'search_repository.dart';

/// Top-bar unified search box: debounced query against search-service,
/// showing a dropdown of matching users/groups/pages/posts, plus a link to
/// the full `/search` results page.
class SearchBarWidget extends ConsumerStatefulWidget {
  const SearchBarWidget({super.key});

  @override
  ConsumerState<SearchBarWidget> createState() => _SearchBarWidgetState();
}

class _SearchBarWidgetState extends ConsumerState<SearchBarWidget> {
  final _controller = TextEditingController();
  final _layerLink = LayerLink();
  OverlayEntry? _overlayEntry;
  Timer? _debounce;
  SearchResults? _results;
  bool _loading = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _removeOverlay();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String query) {
    _debounce?.cancel();
    if (query.trim().isEmpty) {
      setState(() => _results = null);
      _removeOverlay();
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 350), () async {
      setState(() => _loading = true);
      try {
        final results =
            await ref.read(searchRepositoryProvider).search(query.trim());
        if (!mounted) return;
        setState(() {
          _results = results;
          _loading = false;
        });
        _showOverlay();
      } catch (_) {
        if (mounted) setState(() => _loading = false);
      }
    });
  }

  void _showOverlay() {
    _removeOverlay();
    final overlay = Overlay.of(context);
    _overlayEntry = OverlayEntry(
      builder: (context) => Positioned(
        width: 320,
        child: CompositedTransformFollower(
          link: _layerLink,
          showWhenUnlinked: false,
          offset: const Offset(0, 44),
          child: Material(
            elevation: 4,
            borderRadius: BorderRadius.circular(8),
            child: _ResultsList(
              results: _results,
              onTap: () {
                _controller.clear();
                setState(() => _results = null);
                _removeOverlay();
              },
              onViewAll: () {
                final q = _controller.text.trim();
                _controller.clear();
                setState(() => _results = null);
                _removeOverlay();
                if (q.isNotEmpty) context.push('/search?q=${Uri.encodeQueryComponent(q)}');
              },
            ),
          ),
        ),
      ),
    );
    overlay.insert(_overlayEntry!);
  }

  void _removeOverlay() {
    _overlayEntry?.remove();
    _overlayEntry = null;
  }

  @override
  Widget build(BuildContext context) {
    return CompositedTransformTarget(
      link: _layerLink,
      child: SizedBox(
        width: 280,
        height: 36,
        child: TextField(
          controller: _controller,
          onChanged: _onChanged,
          onSubmitted: (q) {
            final trimmed = q.trim();
            if (trimmed.isEmpty) return;
            _controller.clear();
            setState(() => _results = null);
            _removeOverlay();
            context.push('/search?q=${Uri.encodeQueryComponent(trimmed)}');
          },
          decoration: InputDecoration(
            hintText: 'Tìm kiếm trên TSON',
            prefixIcon: _loading
                ? const Padding(
                    padding: EdgeInsets.all(10),
                    child: SizedBox(
                        height: 14, width: 14, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : const Icon(Icons.search, size: 18),
            isDense: true,
          ),
        ),
      ),
    );
  }
}

class _ResultsList extends StatelessWidget {
  final SearchResults? results;
  final VoidCallback onTap;
  final VoidCallback onViewAll;

  const _ResultsList({required this.results, required this.onTap, required this.onViewAll});

  @override
  Widget build(BuildContext context) {
    final r = results;
    if (r == null || r.isEmpty) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Text('Không tìm thấy kết quả'),
      );
    }
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 420),
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          ...r.users.map((u) => ListTile(
                leading: CircleAvatar(
                  backgroundImage: u.avatarUrl != null ? NetworkImage(u.avatarUrl!) : null,
                  child: u.avatarUrl == null ? Text(u.fullName.isNotEmpty ? u.fullName[0] : '?') : null,
                ),
                title: Text(u.fullName),
                onTap: () {
                  onTap();
                  context.push('/profile/${u.id}');
                },
              )),
          ...r.groups.map((g) => ListTile(
                leading: CircleAvatar(
                  backgroundImage: g.avatarUrl != null ? NetworkImage(g.avatarUrl!) : null,
                  child: g.avatarUrl == null ? const Icon(Icons.groups) : null,
                ),
                title: Text(g.name),
                subtitle: const Text('Nhóm'),
                onTap: () {
                  onTap();
                  context.push('/groups/${g.id}');
                },
              )),
          ...r.pages.map((p) => ListTile(
                leading: CircleAvatar(
                  backgroundImage: p.avatarUrl != null ? NetworkImage(p.avatarUrl!) : null,
                  child: p.avatarUrl == null ? const Icon(Icons.flag) : null,
                ),
                title: Text(p.name),
                subtitle: const Text('Trang'),
                onTap: () {
                  onTap();
                  context.push('/pages/${p.id}');
                },
              )),
          ...r.posts.map((p) => ListTile(
                leading: const Icon(Icons.article_outlined),
                title: Text(p.content ?? '(bài viết không có nội dung)',
                    maxLines: 1, overflow: TextOverflow.ellipsis),
                onTap: () {
                  onTap();
                  context.push('/post/${p.id}');
                },
              )),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.search),
            title: const Text('Xem tất cả kết quả'),
            onTap: onViewAll,
          ),
        ],
      ),
    );
  }
}
