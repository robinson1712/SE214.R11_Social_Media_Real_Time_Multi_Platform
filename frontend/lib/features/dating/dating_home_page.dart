import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/enums.dart';
import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import '../chat/chat_provider.dart';
import '../profile/user_lookup_provider.dart';
import 'dating_profile_setup_page.dart';
import 'dating_provider.dart';
import 'dating_repository.dart';

class DatingHomePage extends ConsumerStatefulWidget {
  const DatingHomePage({super.key});

  @override
  ConsumerState<DatingHomePage> createState() => _DatingHomePageState();
}

class _DatingHomePageState extends ConsumerState<DatingHomePage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController = TabController(length: 2, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final profileAsync = ref.watch(myDatingProfileProvider);

    // No page-level AppBar — AppShell already owns the app's one persistent
    // top bar. The tab switcher and settings icon that used to live in this
    // AppBar are real functionality, so they move inline above the content
    // instead of disappearing with the bar.
    return profileAsync.when(
      data: (profile) {
        if (profile == null) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.favorite_border, size: 48),
                  const SizedBox(height: 12),
                  const Text('Tạo hồ sơ hẹn hò để bắt đầu khám phá'),
                  const SizedBox(height: 12),
                  ElevatedButton(
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const DatingProfileSetupPage()),
                    ),
                    child: const Text('Tạo hồ sơ'),
                  ),
                ],
              ),
            ),
          );
        }
        return Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: TabBar(controller: _tabController, tabs: const [
                    Tab(text: 'Khám phá'),
                    Tab(text: 'Ghép đôi'),
                  ]),
                ),
                IconButton(
                  icon: const Icon(Icons.settings),
                  tooltip: 'Hồ sơ hẹn hò',
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const DatingProfileSetupPage()),
                  ),
                ),
              ],
            ),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: const [_DiscoverTab(), _MatchesTab()],
              ),
            ),
          ],
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Lỗi: $e')),
    );
  }
}

class _DiscoverTab extends ConsumerWidget {
  const _DiscoverTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(candidatesProvider);

    if (state.loading) return const Center(child: CircularProgressIndicator());

    if (state.lastMatchUserId != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('🎉', style: TextStyle(fontSize: 48)),
            const SizedBox(height: 8),
            const Text('Ghép đôi thành công!', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            UserInline(userId: state.lastMatchUserId!, avatarRadius: 24),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => ref.read(candidatesProvider.notifier).refresh(),
              child: const Text('Tiếp tục khám phá'),
            ),
          ],
        ),
      );
    }

    if (state.index >= state.candidates.length) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Hết ứng viên phù hợp lúc này', style: TextStyle(color: Colors.grey.shade600)),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () => ref.read(candidatesProvider.notifier).refresh(),
              child: const Text('Tải lại'),
            ),
          ],
        ),
      );
    }

    final candidate = state.candidates[state.index];
    final profile = ref.watch(userLookupProvider(candidate.profile.id)).value;

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    children: [
                      Avatar(
                          url: profile?.avatarUrl,
                          name: profile?.fullName ?? '',
                          radius: 60,
                          gender: profile?.gender),
                      const SizedBox(height: 12),
                      Text('${profile?.fullName ?? '...'}, ${candidate.profile.age}',
                          style: Theme.of(context).textTheme.headlineSmall),
                      const SizedBox(height: 4),
                      Chip(label: Text('Độ hợp: ${candidate.compatibilityScore}%')),
                      if (candidate.profile.bio != null && candidate.profile.bio!.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text(candidate.profile.bio!, textAlign: TextAlign.center),
                        ),
                      if (candidate.profile.interests.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Wrap(
                            spacing: 6,
                            alignment: WrapAlignment.center,
                            children: candidate.profile.interests
                                .map((i) => Chip(label: Text(i)))
                                .toList(),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: Colors.grey.shade300),
                    icon: const Icon(Icons.close, color: Colors.black87),
                    iconSize: 32,
                    onPressed: () => ref.read(candidatesProvider.notifier).swipe(SwipeAction.pass),
                  ),
                  const SizedBox(width: 32),
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: Colors.pink),
                    icon: const Icon(Icons.favorite, color: Colors.white),
                    iconSize: 32,
                    onPressed: () => ref.read(candidatesProvider.notifier).swipe(SwipeAction.like),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MatchesTab extends ConsumerWidget {
  const _MatchesTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncMatches = ref.watch(matchesProvider);
    final myId = ref.watch(currentAccountIdProvider);

    return asyncMatches.when(
      data: (page) => page.content.isEmpty
          ? Center(child: Text('Chưa có lượt ghép đôi nào', style: TextStyle(color: Colors.grey.shade600)))
          : ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: page.content.length,
              itemBuilder: (context, index) {
                final match = page.content[index];
                final otherId = myId == null ? '' : match.otherUserId(myId);
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: UserAvatar(userId: otherId, radius: 22),
                    title: UserNameText(userId: otherId),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: 'Nhắn tin',
                          icon: const Icon(Icons.chat_bubble_outline),
                          onPressed: () async {
                            final conversation = await ref
                                .read(chatProvider.notifier)
                                .startConversationWith(otherId);
                            if (context.mounted) context.go('/chat/${conversation.id}');
                          },
                        ),
                        TextButton(
                          onPressed: () async {
                            await ref.read(datingRepositoryProvider).unmatch(match.id);
                            ref.invalidate(matchesProvider);
                          },
                          child: const Text('Huỷ ghép đôi'),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Lỗi: $e')),
    );
  }
}
