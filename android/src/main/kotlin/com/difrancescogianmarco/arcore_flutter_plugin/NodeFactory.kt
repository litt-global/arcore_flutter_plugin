package com.difrancescogianmarco.arcore_flutter_plugin

import android.content.Context
import android.util.Log
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.google.ar.sceneform.Node

typealias NodeHandler = (Node?, Throwable?) -> Unit

class NodeFactory {

    companion object {
        val TAG: String = NodeFactory::class.java.name

        fun makeNode(context: Context, flutterNode: FlutterArCoreNode, handler: NodeHandler) {
            Log.i(TAG, flutterNode.toString())
            val node = flutterNode.buildNode()
            RenderableCustomFactory.makeRenderable(context, flutterNode) { renderable, texture, material, t ->
                Log.d("stvn", "HahHhahaha texture -1")
                if (renderable != null) {
                    node.renderable = renderable
                    Log.d("stvn", "HahHhahaha texture 0")
                    if (texture != null) {
                        Log.d("stvn", "HahHhahaha texture 1")
                        node.renderableInstance!!.material.setInt("baseColorIndex", 0)
                        node.renderableInstance!!.material.setTexture("baseColorMap", texture)
                        Log.d("stvn", "HahHhahaha texture 2")
                    }
                    handler(node, null)
                }else{
                    handler(null,t)
                }
            }
        }
    }
}