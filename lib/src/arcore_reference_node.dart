import 'package:vector_math/vector_math_64.dart';

import 'package:flutter/widgets.dart';
import 'arcore_node.dart';
import 'arcore_media_info.dart';
import 'package:arcore_flutter_plugin/src/shape/arcore_shape.dart';

class ArCoreReferenceNode extends ArCoreNode {
  /// Filename of sfb object in assets folder (generated with Import Sceneform Asset)
  /// https://developers.google.com/ar/develop/java/sceneform/import-assets
  final String? object3DFileName;

  /// Url of gltf object for remote rendering
  final String? objectUrl;

  // To spin the node
  final ValueNotifier<double>? degreesPerSecond;

  ArCoreReferenceNode({
    String? name,
    this.object3DFileName,
    this.objectUrl,
    ArCoreMediaInfo? mediaInfo,
    List<ArCoreNode> children = const [],
    required ArCoreShape shape,
    required Vector3 position,
    required Vector3 scale,
    required Vector4 rotation,
    this.degreesPerSecond,
  }) : super(
          name: name,
          mediaInfo: mediaInfo,
          children: children,
          shape: shape,
          position: position,
          scale: scale,
          rotation: rotation,
        );

  @override
  Map<String, dynamic> toMap() => <String, dynamic>{
        'object3DFileName': this.object3DFileName,
        'objectUrl': this.objectUrl,
        'degreesPerSecond': this.degreesPerSecond?.value,
      }..addAll(super.toMap());
}
