import 'package:arcore_flutter_plugin/src/arcore_node.dart';
import 'package:flutter/widgets.dart';
import 'package:vector_math/vector_math_64.dart';

import 'package:arcore_flutter_plugin/src/shape/arcore_shape.dart';

class ArCoreRotatingNode extends ArCoreNode {
  ArCoreRotatingNode({
    this.shape,
    double? degreesPerSecond,
    required Vector3 position,
    required Vector3 scale,
    required Vector4 rotation,
    String? name,
  })  : degreesPerSecond = ValueNotifier(90.0),
        super(
          shape: shape,
          name: name,
          position: position,
          scale: scale,
          rotation: rotation,
        );

  final ArCoreShape? shape;

  final ValueNotifier<double> degreesPerSecond;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'degreesPerSecond': this.degreesPerSecond.value,
      }
        ..addAll(super.toMap())
        ..removeWhere((String k, dynamic v) => v == null);
}
