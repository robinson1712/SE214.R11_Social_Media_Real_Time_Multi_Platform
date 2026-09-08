import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/app_notification.dart';
import '../../core/models/enums.dart';
import '../../core/ws/stomp_service.dart';
import 'notification_repository.dart';

const int _kPageSize = 20;

class NotificationState {
  final List<AppNotification> items;
  final int unreadCount;
  final bool loading;

  /// Scrolling down the bell dropdown loads older notifications page by
  /// page (replacing the old separate "view all" page) — these track that.
  final bool loadingMore;
  final bool hasMore;
  final int nextPage;

  const NotificationState({
    this.items = const [],
    this.unreadCount = 0,
    this.loading = false,
    this.loadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
  });

  NotificationState copyWith({
    List<AppNotification>? items,
    int? unreadCount,
    bool? loading,
    bool? loadingMore,
    bool? hasMore,
    int? nextPage,
  }) =>
      NotificationState(
        items: items ?? this.items,
        unreadCount: unreadCount ?? this.unreadCount,
        loading: loading ?? this.loading,
        loadingMore: loadingMore ?? this.loadingMore,
        hasMore: hasMore ?? this.hasMore,
        nextPage: nextPage ?? this.nextPage,
      );
}

class NotificationNotifier extends StateNotifier<NotificationState> {
  final NotificationRepository _repository;
  final _socket = StompService();

  NotificationNotifier(this._repository) : super(const NotificationState(loading: true)) {
    _load();
    _connectSocket();
  }

  Future<void> _load() async {
    try {
      final page = await _repository.list(page: 0, size: _kPageSize);
      final items = page.content.where(_belongsHere).toList();
      final unread = items.where((n) => !n.read).length;
      if (!mounted) return;
      state = state.copyWith(
        items: items,
        unreadCount: unread,
        loading: false,
        hasMore: !page.last,
        nextPage: 1,
      );
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loading: false);
    }
  }

  /// Fetches the next page of older notifications and appends them —
  /// triggered by scrolling toward the bottom of the bell dropdown.
  Future<void> loadMore() async {
    if (state.loadingMore || !state.hasMore || state.loading) return;
    state = state.copyWith(loadingMore: true);
    try {
      final page = await _repository.list(page: state.nextPage, size: _kPageSize);
      final newItems = page.content.where(_belongsHere).toList();
      if (!mounted) return;
      state = state.copyWith(
        items: [...state.items, ...newItems],
        loadingMore: false,
        hasMore: !page.last,
        nextPage: state.nextPage + 1,
      );
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loadingMore: false);
    }
  }

  /// New messages get their own badge on the chat icon (Messenger/Zalo
  /// style) instead of cluttering the notification bell/page — the backend
  /// still emits a MESSAGE-type notification per message for other clients,
  /// but this app filters it out here.
  bool _belongsHere(AppNotification n) => n.type != NotificationType.message;

  Future<void> _connectSocket() async {
    await _socket.connect(
      '/ws-notifications',
      onConnected: () {
        _socket.subscribe('/user/queue/notifications', (body) {
          if (!mounted) return;
          final notification = AppNotification.fromJson(body);
          if (!_belongsHere(notification)) return;
          state = state.copyWith(
            items: [notification, ...state.items],
            unreadCount: state.unreadCount + 1,
          );
        });
      },
    );
  }

  Future<void> markRead(String id) async {
    final updated = await _repository.markRead(id);
    if (!mounted) return;
    state = state.copyWith(
      items: state.items.map((n) => n.id == id ? updated : n).toList(),
      unreadCount: (state.unreadCount - 1).clamp(0, 1 << 30),
    );
  }

  Future<void> markAllRead() async {
    await _repository.markAllRead();
    if (!mounted) return;
    state = state.copyWith(
      items: state.items.map((n) => AppNotification(
            id: n.id,
            recipientId: n.recipientId,
            actorId: n.actorId,
            type: n.type,
            targetType: n.targetType,
            targetId: n.targetId,
            message: n.message,
            read: true,
            createdAt: n.createdAt,
          )).toList(),
      unreadCount: 0,
    );
  }

  Future<void> delete(String id) async {
    AppNotification? removed;
    for (final n in state.items) {
      if (n.id == id) removed = n;
    }
    final wasUnread = removed != null && !removed.read;
    state = state.copyWith(
      items: state.items.where((n) => n.id != id).toList(),
      unreadCount: wasUnread ? (state.unreadCount - 1).clamp(0, 1 << 30) : state.unreadCount,
    );
    try {
      await _repository.delete(id);
    } catch (_) {
      // Best-effort: leave it removed locally even if the backend call fails
      // (e.g. it was already deleted from another tab); a stale item
      // reappearing after refresh is preferable to a hung UI here.
    }
  }

  @override
  void dispose() {
    _socket.disconnect();
    super.dispose();
  }
}

final notificationProvider = StateNotifierProvider<NotificationNotifier, NotificationState>(
  (ref) => NotificationNotifier(ref.watch(notificationRepositoryProvider)),
);
