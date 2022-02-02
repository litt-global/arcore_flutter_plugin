class ArCoreMediaInfo {
  ArCoreMediaInfo({
    required this.isVideo,
    required this.isGif,
    required this.isMuted,
    required this.rotate,
    required this.chromaColor,
    required this.width,
    required this.height,
  })  : assert(width > 0),
        assert(height > 0);

  final bool isVideo;
  final bool isGif;
  final bool isMuted;
  final int rotate;
  final int chromaColor;
  final int width;
  final int height;

  Map<String, dynamic> toMap() => <String, dynamic>{'isVideo': isVideo, 'isGif': isGif, 'isMuted': isMuted, 'rotate': rotate, 'chromaColor': chromaColor, 'width': width, 'height': height}..removeWhere((String k, dynamic v) => v == null);
}
