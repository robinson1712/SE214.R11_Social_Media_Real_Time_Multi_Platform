import 'dart:convert';

import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../api/dio_client.dart';
import '../auth/token_storage.dart';

/// Thin wrapper around a single STOMP-over-WebSocket connection to one of
/// the backend's two Spring endpoints (`/ws` for chat, `/ws-notifications`
/// for notifications). Both are registered with `.withSockJS()` on the
/// server, whose raw-websocket fallback transport lives at
/// `<endpoint>/websocket` — that's the path a plain STOMP client (no SockJS
/// framing) connects to.
///
/// Auth: the gateway's JWT filter accepts the token as a `?token=` query
/// param on the upgrade request (same mechanism the REST calls use via the
/// `Authorization` header, which a WebSocket handshake can't always carry
/// from a browser).
class StompService {
  StompClient? _client;
  bool _connected = false;

  bool get isConnected => _connected;

  Future<void> connect(
    String endpointPath, {
    required void Function() onConnected,
    void Function(dynamic error)? onError,
  }) async {
    final token = await TokenStorage.instance.readAccessToken();
    if (token == null) return;

    final wsBase = apiBaseUrl.replaceFirst(RegExp(r'^http'), 'ws');
    final url = '$wsBase$endpointPath/websocket?token=$token';

    _client = StompClient(
      config: StompConfig(
        url: url,
        onConnect: (frame) {
          _connected = true;
          onConnected();
        },
        onWebSocketError: (dynamic error) => onError?.call(error),
        onStompError: (frame) => onError?.call(frame.body),
        onDisconnect: (frame) => _connected = false,
        reconnectDelay: const Duration(seconds: 5),
      ),
    );
    _client!.activate();
  }

  /// Subscribes to a user-scoped destination (e.g. `/user/queue/messages`)
  /// and decodes each frame body as JSON before handing it to [onMessage].
  void subscribe(String destination, void Function(Map<String, dynamic> body) onMessage) {
    _client?.subscribe(
      destination: destination,
      callback: (frame) {
        if (frame.body == null) return;
        onMessage(jsonDecode(frame.body!) as Map<String, dynamic>);
      },
    );
  }

  void send(String destination, Map<String, dynamic> body) {
    _client?.send(destination: destination, body: jsonEncode(body));
  }

  void disconnect() {
    _client?.deactivate();
    _connected = false;
  }
}
