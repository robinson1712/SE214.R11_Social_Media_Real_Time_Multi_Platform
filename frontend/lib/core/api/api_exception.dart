/// Thrown by repositories when a backend call fails, carrying the
/// `ApiResponse.message` from the server when available.
class ApiException implements Exception {
  final String message;
  final int? statusCode;

  const ApiException({required this.message, this.statusCode});

  @override
  String toString() => message;
}
