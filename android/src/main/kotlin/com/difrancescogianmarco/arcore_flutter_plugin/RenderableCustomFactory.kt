package com.difrancescogianmarco.arcore_flutter_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import java.util.function.Consumer

import android.widget.RelativeLayout.LayoutParams
import android.os.StrictMode

import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.google.ar.sceneform.rendering.*
import java.net.URL

typealias ModelHandler = (Renderable?, Throwable?) -> Unit
typealias MaterialHandler = (Texture?, Material?, Throwable?) -> Unit
typealias RenderableHandler = (Renderable?, Texture?, Material?, Throwable?) -> Unit

class RenderableCustomFactory {

    companion object {

        val TAG = "RenderableCustomFactory"

        @SuppressLint("ShowToast")
        fun makeRenderable(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: RenderableHandler) {

            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)

            makeTextureAndMaterial(context, flutterArCoreNode) { texture, material, throwable ->
                makeModel(context, flutterArCoreNode, texture, material) { model, throwable ->
                    model?.isShadowCaster = false
                    model?.isShadowCaster = false
                    handler(model, texture, material, throwable)
                }
            }
        }

        private fun makeTextureAndMaterial(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: MaterialHandler) {
             if (flutterArCoreNode.objectUrl != null) {
                if (flutterArCoreNode.mediaInfo == null) {
                    // 3D model
                    handler(null, null, null)
                } else if (flutterArCoreNode.mediaInfo.isVideo) {
                    // Video
                    makeVideoMaterial(context, flutterArCoreNode, handler)
                } else if (flutterArCoreNode.mediaInfo.isGif) {
                    // GIF
                    handler(null, null, null)
                } else {
                    // Static image
                    makeImageMaterial(context, flutterArCoreNode, handler)
                }
            } else if (flutterArCoreNode.shape?.materials != null && flutterArCoreNode.shape?.materials?.size!! > 0 ) {
                // Shapes
                makeShapeMaterial(context, flutterArCoreNode, handler)
            } else {
                handler(null, null, null)
            }
        }

        private fun makeShapeMaterial(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: MaterialHandler) {
//            val texture = flutterArCoreNode.shape?.materials?.first()?.texture
            val textureBytes = flutterArCoreNode.shape?.materials?.first()?.textureBytes
            val color = flutterArCoreNode.shape?.materials?.first()?.color
            if (textureBytes != null) {
//                val isPng = texture.endsWith("png")
                val isPng = true

                val builder = com.google.ar.sceneform.rendering.Texture.builder()
//                builder.setSource(context, Uri.parse(texture))
                builder.setSource(BitmapFactory.decodeByteArray(textureBytes, 0, textureBytes.size))
                builder.build().thenAccept { texture ->
                    MaterialCustomFactory.makeWithTexture(context, texture, isPng, flutterArCoreNode.shape.materials[0])?.thenAccept { material ->
                        handler(null, material, null)
                    }?.exceptionally { throwable ->
                        Log.i(TAG, "texture error ${throwable}")
                        handler(null, null, throwable)
                        return@exceptionally null
                    }
                }
            } else if (color != null) {
                MaterialCustomFactory.makeWithColor(context, flutterArCoreNode.shape.materials[0])
                        ?.thenAccept { material: Material ->
                            handler(null, material, null)
                        }?.exceptionally { throwable ->
                            Log.i(TAG, "material error ${throwable}")
                            handler(null, null, throwable)
                            return@exceptionally null
                        }
            } else {
                handler(null, null, null)
            }
        }

        private fun makeVideoMaterial(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: MaterialHandler) {
            val filamentEngine = EngineInstance.getEngine().getFilamentEngine()

            MaterialBuilder.init()

            val materialBuilder = MaterialBuilder()
                    // By default, materials are generated only for DESKTOP. Since we're an Android
                    // app, we set the platform to MOBILE.
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .name("Plain Video Material")
                    .require(MaterialBuilder.VertexAttribute.UV0)
                    // Defaults to UNLIT because it's the only emissive one
                    .shading(MaterialBuilder.Shading.UNLIT)
                    .doubleSided(flutterArCoreNode.shape == null)
                    .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
                    .optimization(MaterialBuilder.Optimization.NONE)

            var materialPackage: MaterialPackage
            var rotateUv = ""

            if (flutterArCoreNode.mediaInfo?.rotate == 90 || flutterArCoreNode.mediaInfo?.rotate == 270) {
                rotateUv =
                        "    float angle = -radians(90.0);\n" +
                        "    mat2 rotation = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));\n" +
                        "    vec2 pivot = vec2(0.5, 0.5);\n" +
                        "    uv = rotation * (uv - pivot) + pivot;\n"
            }

            if(flutterArCoreNode.mediaInfo?.chromaColor != null){
                val shaderCode =
                        "vec3 desaturate(vec3 color, float amount) {\n" +
                        "    // Convert color to grayscale using Luma formula:\n" +
                        "    // https://en.wikipedia.org/wiki/Luma_%28video%29\n" +
                        "    vec3 gray = vec3(dot(vec3(0.2126, 0.7152, 0.0722), color));\n" +
                        "    return vec3(mix(color, gray, amount));\n" +
                        "}\n" +
                        "void material(inout MaterialInputs material) {\n" +
                        "    prepareMaterial(material);\n" +
                        "    vec2 uv = getUV0();\n" +
                        "    ${rotateUv}" +
                        "    vec4 color = texture(materialParams_videoTexture, uv).rgba;\n" +
                        "    vec3 keyColor = materialParams.chromaKeyColor.rgb;\n" +
                        "    float threshold = 0.775;\n" +
                        "    float slope = 0.01;\n" +
                        "    float distance = abs(length(abs(keyColor - color.rgb)));\n" +
                        "    float edge0 = threshold * (1.0 - slope);\n" +
                        "    float alpha = smoothstep(edge0, threshold, distance);\n" +
                        "    color.rgb = desaturate(color.rgb, 1.0 - (alpha * alpha * alpha));\n" +
                        "\n" +
                        "    material.baseColor.a = alpha;\n" +
                        "    material.baseColor.rgb = inverseTonemapSRGB(color.rgb);\n" +
                        "    material.baseColor.rgb *= material.baseColor.a;\n" +
                        "}\n"

                materialPackage = materialBuilder
                        .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "chromaKeyColor")
                        .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                        .material(shaderCode)
                        .build()
            } else {
                val shaderCode =
                        "void material(inout MaterialInputs material) {\n" +
                        "    prepareMaterial(material);\n" +
                        "    vec2 uv = getUV0();\n" +
                        "    ${rotateUv}" +
                        "    material.baseColor = texture(materialParams_videoTexture, uv).rgba;\n" +
                        "}\n";

                materialPackage = materialBuilder
                        .blending(MaterialBuilder.BlendingMode.OPAQUE)
                        .material(shaderCode)
                        .build(filamentEngine)
            }


            if (materialPackage.isValid()) {
                val buffer = materialPackage.getBuffer()
                Material.builder()
                        .setSource(buffer)
                        .build()
                        .thenAccept {material ->
                            handler(null, material, null)
                        }
                        .exceptionally { throwable ->
                            handler(null, null, throwable)
                            Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                            null
                        }
            }

            MaterialBuilder.shutdown()
        }

        private fun makeImageMaterial(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: MaterialHandler) {
            val url = URL(flutterArCoreNode.objectUrl)
            val stream = url.openConnection().getInputStream()
            var bmp = BitmapFactory.decodeStream(stream)

            if (bmp.config != Bitmap.Config.ARGB_8888) {
                bmp = bmp.copy(Bitmap.Config.ARGB_8888,true)
            }

            Texture.builder()
                    .setSampler(Texture.Sampler.builder()
                            .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
                            .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                            .setWrapMode(Texture.Sampler.WrapMode.MIRRORED_REPEAT)
                            .build())
                    //.setSource(context, Uri.parse(flutterArCoreNode.objectUrl))
                    .setSource(bmp)
                    .setUsage(Texture.Usage.COLOR)
                    .build()
                    .thenAccept { texture ->
                        MaterialCustomFactory.makeWithTexture(context, texture, false, flutterArCoreNode.shape!!.materials[0])
                                ?.thenAccept { material: Material ->
                                    handler(texture, material, null)
                                }?.exceptionally { throwable ->
                                    Log.i(TAG, "material error ${throwable}")
                                    handler(null, null, throwable)
                                    return@exceptionally null
                                }
                    }
                    .exceptionally { throwable ->
                        handler(null, null, throwable)
                        Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                        null
                    }
        }

        private fun makeModel(context: Context, flutterArCoreNode: FlutterArCoreNode, texture: Texture?, material: Material?, handler: ModelHandler) {
            if (flutterArCoreNode.shape != null) {
                // Shapes
                makeShapeModel(context, flutterArCoreNode, material!!, handler)
            } else if (flutterArCoreNode.objectUrl != null) {
                if (flutterArCoreNode.mediaInfo == null) {
                    // 3D model
                    make3dModel(context, flutterArCoreNode, handler)
                }
                else if (flutterArCoreNode.mediaInfo.isVideo) {
                    // Video
                    makeVideoModel(context, flutterArCoreNode, handler)
                } else if (flutterArCoreNode.mediaInfo.isGif) {
                    // GIF
                    makeGifModel(context, flutterArCoreNode, handler)
                } else {
                    // Static image
                    makeImageModel(context, flutterArCoreNode, handler)
                }
            }
        }

        private fun make3dModel(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: ModelHandler) {
            ModelRenderable.builder()
                    .setSource(context, Uri.parse(flutterArCoreNode.objectUrl))
                    .setIsFilamentGltf(true)
                    .build()
                    .thenAccept { model ->
                        handler(model, null)
                    }
                    .exceptionally { throwable ->
                        handler(null, throwable)
                        Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                        null
                    }
        }

        private fun makeShapeModel(context: Context, flutterArCoreNode: FlutterArCoreNode, material: Material, handler: ModelHandler) {
            val renderable = flutterArCoreNode.shape?.buildShape(material)
            handler(renderable, null)
        }

        private fun makeImageModel(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: ModelHandler) {
            val image = ImageView(context)
            image.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

            // val bmp = BitmapFactory.decodeByteArray(flutterArCoreNode.mediaInfo.bytes, 0, flutterArCoreNode.mediaInfo.bytes.size)
            // mediaInfo.setImageBitmap(Bitmap.createScaledBitmap(bmp, flutterArCoreNode.mediaInfo.width,
            // flutterArCoreNode.mediaInfo.height, false))

            val url = URL(flutterArCoreNode.objectUrl)
            val stream = url.openConnection().getInputStream()
            val bmp = BitmapFactory.decodeStream(stream)
            image.setImageBitmap(bmp)

            ViewRenderable.builder().setView(context, image)
                    .build()
                    .thenAccept(Consumer { renderable: ViewRenderable -> handler(renderable, null) })
                    .exceptionally { throwable ->
                        Log.e(TAG, "Unable to load mediaInfo renderable.", throwable)
                        handler(null, throwable)
                        return@exceptionally null
                    }
        }

        private fun makeGifModel(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: ModelHandler) {
            val gif = CustomGifView(context, flutterArCoreNode)
            gif.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

            ViewRenderable.builder().setView(context, gif)
                    .build()
                    .thenAccept(Consumer { renderable: ViewRenderable -> handler(renderable, null) })
                    .exceptionally { throwable ->
                        Log.e(TAG, "Unable to load mediaInfo renderable.", throwable)
                        handler(null, throwable)
                        return@exceptionally null
                    }
        }

        private fun makeVideoModel(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: ModelHandler) {
            ModelRenderable.builder()
                    .setSource(context, Uri.parse("models/vertical_plane_1920x1080.glb"))
                    .setIsFilamentGltf(true)
                    .build()
                    .thenAccept { model ->
                        handler(model, null)
                    }
                    .exceptionally { throwable ->
                        handler(null, throwable)
                        Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                        null
                    }
        }
    }
}





//            // Textured static mediaInfo
//            Texture.builder()
//                    .setSampler(Texture.Sampler.builder()
//                            .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
//                            .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
//                            .setWrapMode(Texture.Sampler.WrapMode.MIRRORED_REPEAT)
//                            .build())
//                    .setSource(context, Uri.parse("textures/rock.jpg"))
//                    .setUsage(Texture.Usage.COLOR)
//                    .build()
//                    .thenAccept { texture ->
//                        MaterialCustomFactory.makeWithTexture(context, texture, false, flutterArCoreNode.shape!!.materials[0])
//                                ?.thenAccept { material: Material ->
////                                    val xxx = ShapeFactory.makeCube(Vector3(0.5f, 0.5f, 0.5f), Vector3(0.0f, 0.15f, 0.0f), material)
////                                    handler(xxx, null, null, null)
//
//                                    makeModel(context, flutterArCoreNode, texture, material) { model, throwable ->
//                                        handler(model, null, null, throwable)
//                                    }
//
//                                }?.exceptionally { throwable ->
//                                    Log.i(TAG, "material error ${throwable}")
//                                    handler(null, null, null, throwable)
//                                    return@exceptionally null
//                                }
//                    }
//                    .exceptionally { throwable ->
//                        handler(null, null, null, throwable)
//                        Log.i(TAG, "renderable error ${throwable.localizedMessage}")
//                        null
//                    }
//
//            return



