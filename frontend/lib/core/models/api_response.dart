/// Mirrors common-lib's `ApiResponse<T>` envelope that wraps every backend response:
/// `{ success, message, data, timestamp }`.
class ApiResponse<T> {
  final bool success;
  final String message;
  final T? data;
  final DateTime? timestamp;

  const ApiResponse({
    required this.success,
    required this.message,
    this.data,
    this.timestamp,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic json) fromJsonT,
  ) {
    final rawData = json['data'];
    return ApiResponse<T>(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String? ?? '',
      data: rawData == null ? null : fromJsonT(rawData),
      timestamp: json['timestamp'] == null
          ? null
          : DateTime.tryParse(json['timestamp'] as String),
    );
  }
}
