import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/auth_provider.dart';
import '../../features/auth/login_page.dart';
import '../../features/auth/register_page.dart';
import '../../features/chat/chat_split_page.dart';
import '../../features/dating/dating_home_page.dart';
import '../../features/fanpages/fanpage_detail_page.dart';
import '../../features/fanpages/fanpages_list_page.dart';
import '../../features/feed/feed_page.dart';
import '../../features/groups/group_detail_page.dart';
import '../../features/groups/groups_list_page.dart';
import '../../features/moderation/admin_reports_page.dart';
import '../../features/posts/post_detail_page.dart';
import '../../features/profile/friends_page.dart';
import '../../features/profile/profile_page.dart';
import '../../features/reels/reels_feed_page.dart';
import '../../features/saved/saved_page.dart';
import '../../features/search/search_results_page.dart';
import '../../shared/widgets/app_shell.dart';

class _GoRouterRefreshNotifier extends ChangeNotifier {
  _GoRouterRefreshNotifier(Ref ref) {
    ref.listen(authProvider, (previous, next) {
      if (previous?.status != next.status) notifyListeners();
    });
  }
}

final appRouterProvider = Provider<GoRouter>((ref) {
  final refreshNotifier = _GoRouterRefreshNotifier(ref);

  // go_router has a long-standing bug/design gap on Flutter web: by
  // default, imperative navigation (context.push, used almost everywhere
  // in this app — post/profile/group/etc. detail pages) only updates
  // go_router's own internal state, never the actual browser History API
  // — so the address bar never changes, and a reload always lands back on
  // whatever URL the bar was ACTUALLY last set to (e.g. '/'), even though
  // the app was visibly showing a different page. context.go DOES sync
  // correctly; this flag makes push/pushReplacement etc. behave the same
  // way. It's a static field read at GoRouter construction time, not a
  // constructor parameter — must be set before `GoRouter(...)` runs.
  // Confirmed via a live debug banner that go_router's own tracked URI was
  // already correct immediately after push while window.location.href
  // stayed stale, and that a raw history.pushState call worked fine — the
  // browser itself was never the problem. See
  // https://github.com/flutter/flutter/issues/134318
  GoRouter.optionURLReflectsImperativeAPIs = true;

  return GoRouter(
    // No `initialLocation` override and no redirect-to-a-splash-route while
    // auth is bootstrapping — either one means a plain page reload lands
    // somewhere other than the URL that was actually in the address bar.
    // Instead, the app just stays on whatever route the browser asked for;
    // SocialApp shows a loading overlay on top of it (see main.dart) until
    // auth status resolves, then this redirect only fires for the cases
    // that genuinely need to send you elsewhere.
    refreshListenable: refreshNotifier,
    redirect: (context, state) {
      final authState = ref.read(authProvider);
      final status = authState.status;
      final loggingIn = state.matchedLocation == '/login' ||
          state.matchedLocation == '/register';

      if (status == AuthStatus.unknown) return null;
      if (status == AuthStatus.unauthenticated) return loggingIn ? null : '/login';
      if (status == AuthStatus.authenticated && loggingIn) return '/';

      if (state.matchedLocation.startsWith('/admin') &&
          !(authState.account?.isAdmin ?? false)) {
        return '/';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
      GoRoute(
          path: '/register', builder: (context, state) => const RegisterPage()),
      ShellRoute(
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(path: '/', builder: (context, state) => const FeedPage()),
          GoRoute(
            path: '/post/:id',
            builder: (context, state) =>
                PostDetailPage(postId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: '/profile/:id',
            builder: (context, state) =>
                ProfilePage(userId: state.pathParameters['id']!),
          ),
          GoRoute(path: '/friends', builder: (context, state) => const FriendsPage()),
          GoRoute(path: '/groups', builder: (context, state) => const GroupsListPage()),
          GoRoute(
            path: '/groups/:id',
            builder: (context, state) =>
                GroupDetailPage(groupId: state.pathParameters['id']!),
          ),
          GoRoute(path: '/pages', builder: (context, state) => const FanpagesListPage()),
          GoRoute(
            path: '/pages/:id',
            builder: (context, state) =>
                FanpageDetailPage(pageId: state.pathParameters['id']!),
          ),
          GoRoute(path: '/reels', builder: (context, state) => const ReelsFeedPage()),
          GoRoute(path: '/dating', builder: (context, state) => const DatingHomePage()),
          GoRoute(path: '/saved', builder: (context, state) => const SavedPage()),
          GoRoute(path: '/chat', builder: (context, state) => const ChatSplitPage()),
          GoRoute(
            path: '/chat/:id',
            builder: (context, state) =>
                ChatSplitPage(conversationId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: '/search',
            builder: (context, state) =>
                SearchResultsPage(query: state.uri.queryParameters['q'] ?? ''),
          ),
          GoRoute(
              path: '/admin/reports', builder: (context, state) => const AdminReportsPage()),
        ],
      ),
    ],
  );
});
