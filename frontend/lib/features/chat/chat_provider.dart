import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/chat.dart';
import '../../core/models/user_profile.dart';
import '../../core/ws/stomp_service.dart';
import '../auth/auth_provider.dart';
import '../profile/profile_provider.dart';
import 'chat_repository.dart';

class ChatState {
  final List<Conversation> conversations;
  final Map<String, List<ChatMessage>> messagesByConversation;
  final bool loadingConversations;
  final Set<String> loadingMessagesFor;

  /// Conversations currently open on screen — either the full-page thread
  /// or a floating window — an incoming message for one of these doesn't
  /// count toward the unread badge since the user is already looking at it.
  final Set<String> activeConversationIds;

  /// Messenger/Zalo-style unread badge count, per conversation. Only
  /// tracks messages received live over the socket this session — the
  /// backend has no "unread count per conversation" REST endpoint to seed
  /// this from on first load, so a conversation with an unread backlog
  /// from before this session started won't show a stale count until a
  /// new message arrives.
  final Map<String, int> unreadByConversation;

  /// Facebook-desktop-style floating chat windows, ordered oldest-first;
  /// the newest sits closest to the corner (rightmost), older ones pushed
  /// left as more are opened. Capped at [ChatNotifier.maxOpenWindows].
  final List<String> openWindows;

  /// Floating windows collapsed to just their header bar.
  final Set<String> minimizedWindows;

  const ChatState({
    this.conversations = const [],
    this.messagesByConversation = const {},
    this.loadingConversations = false,
    this.loadingMessagesFor = const {},
    this.activeConversationIds = const {},
    this.unreadByConversation = const {},
    this.openWindows = const [],
    this.minimizedWindows = const {},
  });

  int get totalUnread => unreadByConversation.values.fold(0, (a, b) => a + b);

  ChatState copyWith({
    List<Conversation>? conversations,
    Map<String, List<ChatMessage>>? messagesByConversation,
    bool? loadingConversations,
    Set<String>? loadingMessagesFor,
    Set<String>? activeConversationIds,
    Map<String, int>? unreadByConversation,
    List<String>? openWindows,
    Set<String>? minimizedWindows,
  }) =>
      ChatState(
        conversations: conversations ?? this.conversations,
        messagesByConversation: messagesByConversation ?? this.messagesByConversation,
        loadingConversations: loadingConversations ?? this.loadingConversations,
        loadingMessagesFor: loadingMessagesFor ?? this.loadingMessagesFor,
        activeConversationIds: activeConversationIds ?? this.activeConversationIds,
        unreadByConversation: unreadByConversation ?? this.unreadByConversation,
        openWindows: openWindows ?? this.openWindows,
        minimizedWindows: minimizedWindows ?? this.minimizedWindows,
      );
}

class ChatNotifier extends StateNotifier<ChatState> {
  static const int maxOpenWindows = 3;

  final ChatRepository _repository;
  final Ref _ref;
  final _socket = StompService();

  ChatNotifier(this._repository, this._ref) : super(const ChatState()) {
    loadConversations();
    _connectSocket();
  }

  Future<void> _connectSocket() async {
    await _socket.connect(
      '/ws',
      onConnected: () {
        _socket.subscribe('/user/queue/messages', (body) {
          if (!mounted) return;
          final message = ChatMessage.fromJson(body);
          _onIncomingMessage(message);
        });
      },
    );
  }

  void _onIncomingMessage(ChatMessage message) {
    final existing = List<ChatMessage>.from(
        state.messagesByConversation[message.conversationId] ?? const []);
    existing.insert(0, message);

    final knowsConversation =
        state.conversations.any((c) => c.id == message.conversationId);
    if (!knowsConversation) {
      // First message of a conversation someone else just started with us —
      // we don't have it in `conversations` yet (only fetched once, on
      // connect), so there's no tile for it to update in the .map() below.
      // Refetch the list so it actually shows up instead of silently
      // caching the message somewhere the UI never looks.
      loadConversations();
    }

    final conversations = state.conversations.map((c) {
      if (c.id != message.conversationId) return c;
      return Conversation(
        id: c.id,
        type: c.type,
        participantIds: c.participantIds,
        lastMessagePreview: message.content ?? c.lastMessagePreview,
        lastMessageAt: message.sentAt ?? c.lastMessageAt,
        createdAt: c.createdAt,
      );
    }).toList()
      ..sort((a, b) => (b.lastMessageAt ?? b.createdAt ?? DateTime(0))
          .compareTo(a.lastMessageAt ?? a.createdAt ?? DateTime(0)));

    final myId = _ref.read(currentAccountIdProvider);
    final isMine = myId != null && message.senderId == myId;
    final isOpen = state.activeConversationIds.contains(message.conversationId);
    final unread = Map<String, int>.from(state.unreadByConversation);
    if (!isMine && !isOpen) {
      unread[message.conversationId] = (unread[message.conversationId] ?? 0) + 1;
    }

    state = state.copyWith(
      messagesByConversation: {...state.messagesByConversation, message.conversationId: existing},
      conversations: conversations,
      unreadByConversation: unread,
    );
  }

  /// Call when a chat thread screen or floating window opens — clears its
  /// badge and marks further incoming messages for it as "already seen"
  /// until closed.
  void openConversation(String conversationId) {
    final unread = Map<String, int>.from(state.unreadByConversation)..remove(conversationId);
    final active = Set<String>.from(state.activeConversationIds)..add(conversationId);
    state = state.copyWith(activeConversationIds: active, unreadByConversation: unread);
  }

  void closeConversation(String conversationId) {
    final active = Set<String>.from(state.activeConversationIds)..remove(conversationId);
    state = state.copyWith(activeConversationIds: active);
  }

  /// Opens a Facebook-desktop-style floating chat window for
  /// [conversationId] — bringing it to the front (rightmost slot) if
  /// already open, or adding a new one and evicting the oldest once past
  /// [maxOpenWindows].
  void openFloatingWindow(String conversationId) {
    final windows = List<String>.from(state.openWindows)..remove(conversationId);
    windows.add(conversationId);
    while (windows.length > maxOpenWindows) {
      final evicted = windows.removeAt(0);
      closeConversation(evicted);
    }
    final minimized = Set<String>.from(state.minimizedWindows)..remove(conversationId);
    state = state.copyWith(openWindows: windows, minimizedWindows: minimized);
    openConversation(conversationId);
    loadMessages(conversationId);
    markRead(conversationId);
  }

  void closeFloatingWindow(String conversationId) {
    final windows = List<String>.from(state.openWindows)..remove(conversationId);
    final minimized = Set<String>.from(state.minimizedWindows)..remove(conversationId);
    state = state.copyWith(openWindows: windows, minimizedWindows: minimized);
    closeConversation(conversationId);
  }

  void toggleMinimizeWindow(String conversationId) {
    final minimized = Set<String>.from(state.minimizedWindows);
    if (!minimized.remove(conversationId)) {
      minimized.add(conversationId);
    }
    state = state.copyWith(minimizedWindows: minimized);
  }

  Future<void> loadConversations() async {
    state = state.copyWith(loadingConversations: true);
    try {
      final list = await _repository.conversations();
      if (!mounted) return;
      state = state.copyWith(conversations: list, loadingConversations: false);
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loadingConversations: false);
    }
  }

  Future<void> loadMessages(String conversationId) async {
    final loading = Set<String>.from(state.loadingMessagesFor)..add(conversationId);
    state = state.copyWith(loadingMessagesFor: loading);
    try {
      final page = await _repository.messages(conversationId);
      if (!mounted) return;
      final stillLoading = Set<String>.from(state.loadingMessagesFor)..remove(conversationId);
      state = state.copyWith(
        messagesByConversation: {...state.messagesByConversation, conversationId: page.content},
        loadingMessagesFor: stillLoading,
      );
    } catch (_) {
      if (!mounted) return;
      final stillLoading = Set<String>.from(state.loadingMessagesFor)..remove(conversationId);
      state = state.copyWith(loadingMessagesFor: stillLoading);
    }
  }

  void sendMessage(
    String conversationId,
    String? content, {
    String? mediaUrl,
    String? storyReplyId,
    String? storyReplyPreviewUrl,
  }) {
    _socket.send(
      '/app/chat.send',
      ChatSendRequest(
        conversationId: conversationId,
        content: content,
        mediaUrl: mediaUrl,
        storyReplyId: storyReplyId,
        storyReplyPreviewUrl: storyReplyPreviewUrl,
      ).toJson(),
    );
  }

  Future<void> markRead(String conversationId) async {
    await _repository.markRead(conversationId);
  }

  Future<Conversation> startConversationWith(String otherUserId) async {
    final conversation =
        await _repository.createConversation(CreateConversationRequest(participantIds: [otherUserId]));
    await loadConversations();
    return conversation;
  }

  @override
  void dispose() {
    _socket.disconnect();
    super.dispose();
  }
}

final chatProvider = StateNotifierProvider<ChatNotifier, ChatState>(
  (ref) => ChatNotifier(ref.watch(chatRepositoryProvider), ref),
);

final chatUnreadCountProvider = Provider<int>(
  (ref) => ref.watch(chatProvider.select((s) => s.totalUnread)),
);

/// Online/offline snapshot from `GET /api/chat/presence/{userId}` (Redis-
/// backed, connect/disconnect driven — no push updates exist for this, so
/// it's a point-in-time read rather than a live indicator).
final presenceProvider = FutureProvider.family<bool, String>((ref, userId) async {
  try {
    return await ref.read(chatRepositoryProvider).presence(userId);
  } catch (_) {
    return false;
  }
});

/// Friends who are currently online — powers the right sidebar's "Đang hoạt
/// động" section. Same point-in-time-snapshot caveat as [presenceProvider],
/// just batched into one request instead of one per friend.
final onlineFriendsProvider = FutureProvider<List<UserProfile>>((ref) async {
  final friends = await ref.watch(myFriendsListProvider.future);
  if (friends.isEmpty) return const [];
  try {
    final statuses =
        await ref.read(chatRepositoryProvider).presenceBatch(friends.map((f) => f.id).toList());
    return friends.where((f) => statuses[f.id] == true).toList();
  } catch (_) {
    return const [];
  }
});
