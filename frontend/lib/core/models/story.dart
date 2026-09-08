import 'enums.dart';

/// One piece of text drawn on top of a story's media. Position is fractional
/// (0..1 of the media's width/height) so it renders the same regardless of
/// screen size.
class TextOverlay {
  final String text;
  final String fontFamily;
  final String color;
  final double x;
  final double y;
  final double fontSize;

  const TextOverlay({
    required this.text,
    required this.fontFamily,
    required this.color,
    required this.x,
    required this.y,
    this.fontSize = 24,
  });

  TextOverlay copyWith({String? text, String? fontFamily, String? color, double? x, double? y, double? fontSize}) =>
      TextOverlay(
        text: text ?? this.text,
        fontFamily: fontFamily ?? this.fontFamily,
        color: color ?? this.color,
        x: x ?? this.x,
        y: y ?? this.y,
        fontSize: fontSize ?? this.fontSize,
      );

  factory TextOverlay.fromJson(Map<String, dynamic> json) => TextOverlay(
        text: json['text'] as String? ?? '',
        fontFamily: json['fontFamily'] as String? ?? 'Roboto',
        color: json['color'] as String? ?? '#FFFFFF',
        x: (json['x'] as num?)?.toDouble() ?? 0.5,
        y: (json['y'] as num?)?.toDouble() ?? 0.5,
        fontSize: (json['fontSize'] as num?)?.toDouble() ?? 24,
      );

  Map<String, dynamic> toJson() => {
        'text': text,
        'fontFamily': fontFamily,
        'color': color,
        'x': x,
        'y': y,
        'fontSize': fontSize,
      };
}

/// Fonts available in the story text-overlay editor, matching the Google
/// Fonts family names passed to `GoogleFonts.getFont`.
const List<String> kStoryFonts = [
  'Roboto',
  'Pacifico',
  'Roboto Mono',
  'Playfair Display',
  'Bangers',
  'Lobster',
];

/// Colors available in the story text-overlay editor.
const List<String> kStoryTextColors = [
  '#FFFFFF',
  '#000000',
  '#FF3B30',
  '#FFCC00',
  '#34C759',
  '#007AFF',
  '#AF52DE',
  '#FF2D55',
];

/// Mirrors story-service's `Story` document.
class Story {
  final String id;
  final String authorId;
  final String mediaUrl;
  final StoryMediaType mediaType;
  final String? caption;
  final List<String> viewerIds;
  final List<TextOverlay> textOverlays;
  final DateTime? createdAt;
  final DateTime? expiresAt;

  const Story({
    required this.id,
    required this.authorId,
    required this.mediaUrl,
    required this.mediaType,
    this.caption,
    this.viewerIds = const [],
    this.textOverlays = const [],
    this.createdAt,
    this.expiresAt,
  });

  factory Story.fromJson(Map<String, dynamic> json) => Story(
        id: json['id'] as String,
        authorId: json['authorId'] as String,
        mediaUrl: json['mediaUrl'] as String? ?? '',
        mediaType: StoryMediaTypeJson.fromJson(json['mediaType'] as String?),
        caption: json['caption'] as String?,
        viewerIds: (json['viewerIds'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        textOverlays: (json['textOverlays'] as List<dynamic>? ?? const [])
            .map((e) => TextOverlay.fromJson(e as Map<String, dynamic>))
            .toList(),
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
        expiresAt: json['expiresAt'] == null
            ? null
            : DateTime.tryParse(json['expiresAt'] as String),
      );
}

class CreateStoryRequest {
  final String mediaUrl;
  final StoryMediaType mediaType;
  final String? caption;
  final List<TextOverlay> textOverlays;

  const CreateStoryRequest({
    required this.mediaUrl,
    required this.mediaType,
    this.caption,
    this.textOverlays = const [],
  });

  Map<String, dynamic> toJson() => {
        'mediaUrl': mediaUrl,
        'mediaType': mediaType.toJson(),
        'caption': caption,
        'textOverlays': textOverlays.map((o) => o.toJson()).toList(),
      };
}
