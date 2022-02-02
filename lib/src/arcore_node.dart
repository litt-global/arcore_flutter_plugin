import 'package:arcore_flutter_plugin/src/arcore_media_info.dart';
import 'package:arcore_flutter_plugin/src/utils/vector_utils.dart';
import 'package:flutter/widgets.dart';
import 'package:vector_math/vector_math_64.dart';
import 'package:arcore_flutter_plugin/src/utils/random_string.dart' as random_string;
import 'package:arcore_flutter_plugin/src/shape/arcore_shape.dart';

class ArCoreNode {
  ArCoreNode({
    required this.shape,
    this.mediaInfo,
    String? name,
    required Vector3 position,
    required Vector3 scale,
    required Vector4 rotation,
    this.children = const [],
  })  : name = name ?? random_string.randomString(),
        position = ValueNotifier(position),
        scale = ValueNotifier(scale),
        rotation = ValueNotifier(rotation) /*,
        assert(!(shape != null && mediaInfo != null))*/
  ;

  final List<ArCoreNode> children;

  final ArCoreShape shape;

  final ValueNotifier<Vector3> position;

  final ValueNotifier<Vector3> scale;

  final ValueNotifier<Vector4> rotation;

  final String name;

  final ArCoreMediaInfo? mediaInfo;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'dartType': runtimeType.toString(),
        'shape': shape.toMap(),
        'position': convertVector3ToMap(position.value),
        'scale': convertVector3ToMap(scale.value),
        'rotation': convertVector4ToMap(rotation.value),
        'name': name,
        'mediaInfo': mediaInfo?.toMap(),
        'children': this.children.map((arCoreNode) => arCoreNode.toMap()).toList(),
      }..removeWhere((String k, dynamic v) => v == null);
}
