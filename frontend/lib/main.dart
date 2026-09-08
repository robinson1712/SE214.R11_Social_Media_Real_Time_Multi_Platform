import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/auth_provider.dart';

/// Set from three different error-reporting paths (widget build failures,
/// general Flutter framework errors, and uncaught async/zone errors) — see
/// main() and SocialApp.build below. A crash that only reaches the browser
/// console is invisible to whoever is testing the app without DevTools
/// open, which reads as "the page went blank for no reason"; surfacing it
/// on screen instead turns every future crash into something screenshot-able.
///
/// Shown as a non-destructive overlay (via MaterialApp.router's `builder`)
/// rather than by replacing the whole widget tree — an earlier version
/// swapped out the entire app on any error, which tore down whatever was
/// open underneath (a confirmation dialog, mid-gesture state, ...) and
/// caused its own *second*, unrelated crash the moment a pending tap
/// finished delivering to now-destroyed widgets. That secondary crash was
/// masking the real one.
final ValueNotifier<String?> globalCrashMessage = ValueNotifier(null);

void main() {
  // Without this, Flutter web defaults to hash-based URLs (/#/profile/xyz)
  // — the address bar's visible location doesn't reliably reflect the
  // in-app route across some browsers/extensions, which broke both
  // deep-linking and "stay on this page after a reload".
  usePathUrlStrategy();

  FlutterError.onError = (FlutterErrorDetails details) {
    FlutterError.presentError(details);
    globalCrashMessage.value ??=
        'Flutter error:\n${details.exceptionAsString()}\n\n${details.stack}';
  };

  ErrorWidget.builder = (FlutterErrorDetails details) {
    return Material(
      color: const Color(0xFFFFF0F0),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Text(
          'Đã có lỗi giao diện — chụp màn hình này để báo lỗi:\n\n'
          '${details.exceptionAsString()}\n\n${details.stack}',
          style: const TextStyle(color: Colors.red, fontSize: 11, fontFamily: 'monospace'),
        ),
      ),
    );
  };

  runZonedGuarded(
    () => runApp(const ProviderScope(child: SocialApp())),
    (error, stack) {
      debugPrint('UNCAUGHT ASYNC ERROR: $error\n$stack');
      // First error wins — once something has already gone wrong, later
      // errors are very often just downstream noise from the first one.
      globalCrashMessage.value ??= 'Uncaught async error:\n$error\n\n$stack';
    },
  );
}

class SocialApp extends ConsumerWidget {
  const SocialApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: 'TSON',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      routerConfig: router,
      builder: (context, child) {
        final authStatus = ref.watch(authProvider.select((s) => s.status));
        return Stack(
          children: [
            if (child != null) child,
            // Covers whatever route the app happened to boot/reload into
            // while session verification is still in flight, so a
            // reload never has to *navigate* anywhere just to hide a
            // transient "not logged in yet" flash — see app_router.dart.
            if (authStatus == AuthStatus.unknown)
              const Positioned.fill(
                child: ColoredBox(
                  color: Colors.white,
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
            ValueListenableBuilder<String?>(
              valueListenable: globalCrashMessage,
              builder: (context, message, _) {
                if (message == null) return const SizedBox.shrink();
                return Positioned.fill(
                  child: Material(
                    color: const Color(0xFFFFF0F0),
                    child: SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Row(
                              children: [
                                const Expanded(
                                  child: Text('Đã có lỗi — chụp màn hình này để báo lỗi',
                                      style: TextStyle(
                                          fontWeight: FontWeight.bold, color: Colors.red)),
                                ),
                                TextButton(
                                  onPressed: () => globalCrashMessage.value = null,
                                  child: const Text('Đóng'),
                                ),
                              ],
                            ),
                            const Divider(),
                            Expanded(
                              child: SingleChildScrollView(
                                child: SelectableText(
                                  message,
                                  style: const TextStyle(
                                      color: Colors.red, fontSize: 11, fontFamily: 'monospace'),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ],
        );
      },
    );
  }
}
