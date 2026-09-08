import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/fanpage.dart';
import '../../core/models/page_response.dart';
import '../../shared/widgets/avatar.dart';
import 'create_fanpage_dialog.dart';
import 'fanpage_provider.dart';

/// The Pages section's content pane — mirrors GroupsListPage
/// (groups_list_page.dart): just the list, reading which one to show from
/// [pagesSectionTabProvider]. The section's sidebar ([PagesSidebar] below)
/// is rendered by AppShell itself while any `/pages*` route is active.
class FanpagesListPage extends ConsumerStatefulWidget {
  const FanpagesListPage({super.key});

  @override
  ConsumerState<FanpagesListPage> createState() => _FanpagesListPageState();
}

class _FanpagesListPageState extends ConsumerState<FanpagesListPage> {
  final _searchController = TextEditingController();
  String? _query;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tab = ref.watch(pagesSectionTabProvider);
    // Same centered max-width column as the feed (feed_page.dart) — without
    // this the list stretched across the entire remaining width next to
    // the sidebar, which read as far wider than every other page's content.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 680),
        child: switch (tab) {
          PagesTab.discover => Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                        prefixIcon: Icon(Icons.search), hintText: 'Tìm trang...'),
                    onSubmitted: (v) => setState(() => _query = v.trim()),
                  ),
                ),
                Expanded(child: _FanpageGrid(provider: fanpageListProvider(_query))),
              ],
            ),
          PagesTab.mine => _FanpageGrid(provider: myManagedFanpagesProvider),
        },
      ),
    );
  }
}

/// Facebook-style Pages sidebar: nav between discover/pages-you-manage, a
/// "+" to create a new one, and a quick-jump list of pages you follow.
/// Rendered by AppShell in place of its own generic left nav for any
/// `/pages*` route.
class PagesSidebar extends ConsumerWidget {
  const PagesSidebar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(pagesSectionTabProvider);
    final followed = ref.watch(myFollowedFanpagesProvider);

    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Row(
            children: [
              Text('Trang', style: Theme.of(context).textTheme.headlineSmall),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.add),
                tooltip: 'Tạo trang mới',
                onPressed: () => showCreateFanpageDialog(context),
              ),
            ],
          ),
        ),
        _SidebarItem(
          icon: Icons.explore_outlined,
          label: 'Khám phá',
          selected: selected == PagesTab.discover,
          onTap: () {
            ref.read(pagesSectionTabProvider.notifier).state = PagesTab.discover;
            if (GoRouterState.of(context).matchedLocation != '/pages') context.go('/pages');
          },
        ),
        _SidebarItem(
          icon: Icons.flag_outlined,
          label: 'Trang tôi quản lý',
          selected: selected == PagesTab.mine,
          onTap: () {
            ref.read(pagesSectionTabProvider.notifier).state = PagesTab.mine;
            if (GoRouterState.of(context).matchedLocation != '/pages') context.go('/pages');
          },
        ),
        const Divider(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text('Trang bạn đã theo dõi',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.grey.shade600)),
        ),
        const SizedBox(height: 4),
        followed.when(
          data: (page) => page.content.isEmpty
              ? Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Text('Bạn chưa theo dõi trang nào',
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade500)),
                )
              : Column(
                  children: page.content
                      .map((p) => ListTile(
                            dense: true,
                            leading: Avatar(url: p.avatarUrl, name: p.name, radius: 16),
                            title: Text(p.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                            onTap: () => context.push('/pages/${p.id}'),
                          ))
                      .toList(),
                ),
          loading: () => const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Center(
                child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))),
          ),
          error: (_, __) => const SizedBox.shrink(),
        ),
      ],
    );
  }
}

class _SidebarItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _SidebarItem({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      child: Material(
        color: selected ? Colors.blue.shade50 : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        child: ListTile(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          leading: Icon(icon, color: selected ? Theme.of(context).primaryColor : null),
          title: Text(
            label,
            style: TextStyle(
              fontWeight: selected ? FontWeight.bold : FontWeight.normal,
              color: selected ? Theme.of(context).primaryColor : null,
            ),
          ),
          onTap: onTap,
        ),
      ),
    );
  }
}

class _FanpageGrid extends ConsumerWidget {
  final ProviderListenable<AsyncValue<PageResponse<Fanpage>>> provider;

  const _FanpageGrid({required this.provider});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(provider);
    return async.when(
      data: (page) {
        final content = page.content;
        if (content.isEmpty) {
          return Center(child: Text('Không có trang nào', style: TextStyle(color: Colors.grey.shade600)));
        }
        return ListView(
          padding: const EdgeInsets.all(12),
          children: content
              .map<Widget>((p) => Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: Avatar(url: p.avatarUrl, name: p.name),
                      title: Text(p.name),
                      subtitle: Text('${p.followerCount} người theo dõi'
                          '${p.category != null ? " · ${p.category}" : ""}'),
                      onTap: () => context.push('/pages/${p.id}'),
                    ),
                  ))
              .toList(),
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Lỗi: $e')),
    );
  }
}
