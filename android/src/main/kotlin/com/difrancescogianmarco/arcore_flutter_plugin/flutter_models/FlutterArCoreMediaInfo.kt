package com.difrancescogianmarco.arcore_flutter_plugin.flutter_models

class FlutterArCoreMediaInfo(map: HashMap<String, *>) {

    val isGif: Boolean = map["isGif"] as Boolean
    val isVideo: Boolean = map["isVideo"] as Boolean
    val isMuted: Boolean = map["isMuted"] as Boolean
    val rotate: Int = map["rotate"] as Int
    val chromaColor: Long? = if(map.containsKey("chromaColor")) map["chromaColor"] as Long else null
    val width: Int = map["width"] as Int
    val height: Int = map["height"] as Int

}