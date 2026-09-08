import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/enums.dart';
import '../../shared/widgets/avatar.dart';
import 'create_group_dialog.dart';
import 'group_provider.dart';

/// The Groups section's content pane — just the list (discover search, or
/// your groups), reading which one to show from [groupsSectionTabProvider].
/// The section's own sidebar ([GroupsSidebar] below) is rendered by
/// AppShell itself while any `/groups*` route is active, replacing the
/// app's generic left nav rather than sitting alongside it (see
/// app_shell.dart) — so this page no longer builds one of its own.
class GroupsListPage extends ConsumerStatefulWidget {
  const GroupsListPage({super.key});

  @override
  ConsumerState<GroupsListPage> createState() => _GroupsListPageState();
}

class _GroupsListPageState extends ConsumerState<GroupsListPage> {
  final _searchController = TextEditingController();
  String? _query;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tab = ref.watch(groupsSectionTabProvider);
    // Same centered max-width column as the feed (feed_page.dart) — without
    // this the list stretched across the entire remaining width next to
    // the sidebar, which read as far wider than every other page's content.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 680),
        child: switch (tab) {
          GroupsTab.discover => Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                        prefixIcon: Icon(Icons.search), hintText: 'Tìm nhóm...'),
                    onSubmitted: (v) => setState(() => _query = v.trim()),
                  ),
                ),
                Expanded(child: _GroupGrid(nameFilter: _query)),
              ],
            ),
          GroupsTab.mine => const _MyGroupsGrid(),
        },
      ),
    );
  }
}

/// Facebook-style Groups sidebar: nav between discover/your-groups, a "+"
/// to create a new one, and a quick-jump list of groups you're already in.
/// Rendered by AppShell in place of its own generic left nav for any
/// `/groups*` route.
class GroupsSidebar extends ConsumerWidget {
  const GroupsSidebar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(groupsSectionTabProvider);
    final myGroups = ref.watch(myGroupsProvider);

    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Row(
            children: [
              Text('Nhóm', style: Theme.of(context).textTheme.headlineSmall),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.add),
                tooltip: 'Tạo nhóm mới',
                onPressed: () => showCreateGroupDialog(context),
              ),
            ],
          ),
        ),
        _SidebarItem(
          icon: Icons.explore_outlined,
          label: 'Khám phá',
          selected: selected == GroupsTab.discover,
          onTap: () {
            ref.read(groupsSectionTabProvider.notifier).state = GroupsTab.discover;
            if (GoRouterState.of(context).matchedLocation != '/groups') context.go('/groups');
          },
        ),
        _SidebarItem(
          icon: Icons.groups_outlined,
          label: 'Nhóm của bạn',
          selected: selected == GroupsTab.mine,
          onTap: () {
            ref.read(groupsSectionTabProvider.notifier).state = GroupsTab.mine;
            if (GoRouterState.of(context).matchedLocation != '/groups') context.go('/groups');
          },
        ),
        const Divider(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text('Nhóm bạn đã tham gia',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.grey.shade600)),
        ),
        const SizedBox(height: 4),
        myGroups.when(
          data: (page) => page.content.isEmpty
              ? Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Text('Bạn chưa tham gia nhóm nào',
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade500)),
                )
              : Column(
                  children: page.content
                      .map((g) => ListTile(
                            dense: true,
                            leading: Avatar(url: g.avatarUrl, name: g.name, radius: 16),
                            title: Text(g.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                            onTap: () => context.push('/groups/${g.id}'),
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

class _GroupGrid extends ConsumerWidget {
  final String? nameFilter;

  const _GroupGrid({this.nameFilter});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncGroups = ref.watch(groupListProvider(nameFilter));
    return asyncGroups.when(
      data: (page) => page.content.isEmpty
          ? Center(child: Text('Không có nhóm nào', style: TextStyle(color: Colors.grey.shade600)))
          : ListView(
              padding: const EdgeInsets.all(12),
              children: page.content.map((g) => Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: Avatar(url: g.avatarUrl, name: g.name),
                      title: Text(g.name),
                      subtitle: Text('${g.memberCount} thành viên · ${g.privacy.label}'),
                      onTap: () => context.push('/groups/${g.id}'),
                    ),
                  )).toList(),
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Lỗi: $e')),
    );
  }
}

class _MyGroupsGrid extends ConsumerWidget {
  const _MyGroupsGrid();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncGroups = ref.watch(myGroupsProvider);
    return asyncGroups.when(
      data: (page) => page.content.isEmpty
          ? Center(
              child: Text('Bạn chưa tham gia nhóm nào', style: TextStyle(color: Colors.grey.shade600)))
          : ListView(
              padding: const EdgeInsets.all(12),
              children: page.content.map((g) => Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: Avatar(url: g.avatarUrl, name: g.name),
                      title: Text(g.name),
                      subtitle: Text('${g.memberCount} thành viên'),
                      onTap: () => context.push('/groups/${g.id}'),
                    ),
                  )).toList(),
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Lỗi: $e')),
    );
  }
}
