package com.difrancescogianmarco.arcore_flutter_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import java.util.function.Consumer

import android.widget.RelativeLayout.LayoutParams
import android.os.StrictMode
import android.graphics.Canvas
import android.graphics.Movie

import android.view.View
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.google.ar.sceneform.rendering.*
import java.net.URL

typealias MaterialHandler = (Material?, Throwable?) -> Unit
typealias RenderableHandler = (Renderable?, Texture?, Material?, Throwable?) -> Unit

class CustomGifView : View {
    private var gifMovie: Movie? = null
    var movieWidth: Int = 0
        private set
    var movieHeight: Int = 0
        private set
    var movieDuration: Long = 0
        private set
    private var mMovieStart: Long = 0

    constructor(context: Context, node: FlutterArCoreNode) : super(context) {
        init(context, node)
    }

    private fun init(context: Context, node: FlutterArCoreNode) {
        setFocusable(true)

        // gifMovie = Movie.decodeByteArray(mediaInfo.bytes, 0, mediaInfo.bytes.size)

        val gifInputStream = URL(node.objectUrl).openConnection().getInputStream()

        gifMovie = Movie.decodeStream(gifInputStream)
        movieWidth = gifMovie!!.width()
        movieHeight = gifMovie!!.height()
        movieDuration = gifMovie!!.duration().toLong()
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(movieWidth, movieHeight)
    }

    protected override fun onDraw(canvas: Canvas) {
        val now = android.os.SystemClock.uptimeMillis()
        if (mMovieStart == 0L) {   // first time
            mMovieStart = now
        }

        if (gifMovie != null) {
            var dur = gifMovie!!.duration()
            if (dur == 0) {
                dur = 1000
            }

            val relTime = ((now - mMovieStart) % dur).toInt()

            gifMovie!!.setTime(relTime)
            gifMovie!!.draw(canvas, 0f, 0f)
            invalidate()
        }
    }
}

class RenderableCustomFactory {

    companion object {

        val TAG = "RenderableCustomFactory"

        @SuppressLint("ShowToast")
        fun makeRenderable(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: RenderableHandler) {

            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)

            if (flutterArCoreNode.objectUrl != null && flutterArCoreNode.mediaInfo == null) {
                // 3D model
                var url = flutterArCoreNode.objectUrl

                val localObject = flutterArCoreNode.object3DFileName
                if (localObject != null) {
                    val builder = ModelRenderable.builder()
                    builder.setSource(context, Uri.parse(localObject))
                    builder.build().thenAccept { renderable ->
                        handler(renderable, null, null, null)
                    }.exceptionally { throwable ->
                        Log.e(TAG, "Unable to load Renderable.", throwable);
                        handler(null, null, null, throwable)
                        return@exceptionally null
                    }
                } else if (url != null) {
                    ModelRenderable.builder()
                            .setSource(context, Uri.parse(url))
                            .setIsFilamentGltf(true)
                            .build()
                            .thenAccept { model ->

                                handler(model, null, null, null)
                            }

                            .exceptionally { throwable ->
                                handler(null, null, null, throwable)
                                Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                                null
                            }
                }
            } else if (flutterArCoreNode.objectUrl != null && flutterArCoreNode.mediaInfo != null) {
                if (flutterArCoreNode.mediaInfo.isVideo) {
                    // Opaque video
                    ModelRenderable.builder()
                            .setSource(context, Uri.parse("models/vertical_plane_1920x1080.glb"))
                            .setIsFilamentGltf(true)
                            .build()
                            .thenAccept { model ->

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
                                        .doubleSided(true)
                                        .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
                                        .optimization(MaterialBuilder.Optimization.NONE)

                                var materialPackage: MaterialPackage
                                if(flutterArCoreNode.mediaInfo.chromaColor != null){
                                    Log.d("abc", "using chroma now 0")
                                    materialPackage = materialBuilder
                                            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "chromaKeyColor")
                                            .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                                            .material(
                                                    "vec3 desaturate(vec3 color, float amount) {\n" +
                                                            "    // Convert color to grayscale using Luma formula:\n" +
                                                            "    // https://en.wikipedia.org/wiki/Luma_%28video%29\n" +
                                                            "    vec3 gray = vec3(dot(vec3(0.2126, 0.7152, 0.0722), color));\n" +
                                                            "    return vec3(mix(color, gray, amount));\n" +
                                                            "}\n" +
                                                            "void material(inout MaterialInputs material) {\n" +
                                                            "    prepareMaterial(material);\n" +
                                                            "    vec4 color = texture(materialParams_videoTexture, getUV0()).rgba;\n" +
                                                            "    vec3 keyColor = materialParams.chromaKeyColor.rgb;\n" +
                                                            "    float threshold = 0.675;\n" +
                                                            "    float slope = 0.2;\n" +
                                                            "    float distance = abs(length(abs(keyColor - color.rgb)));\n" +
                                                            "    float edge0 = threshold * (1.0 - slope);\n" +
                                                            "    float alpha = smoothstep(edge0, threshold, distance);\n" +
                                                            "    color.rgb = desaturate(color.rgb, 1.0 - (alpha * alpha * alpha));\n" +
                                                            "\n" +
                                                            "    material.baseColor.a = alpha;\n" +
                                                            "    material.baseColor.rgb = inverseTonemapSRGB(color.rgb);\n" +
                                                            "    material.baseColor.rgb *= material.baseColor.a;\n" +
                                                            "}\n")
                                            .build()
                                } else {
                                    Log.d("abc", "using chroma now 1")
                                    materialPackage = materialBuilder
                                            .blending(MaterialBuilder.BlendingMode.OPAQUE)
                                            .material("void material(inout MaterialInputs material) {\n" +
                                                    "    prepareMaterial(material);\n" +
                                                    "    material.baseColor = texture(materialParams_videoTexture, getUV0()).rgba;\n" +
                                                    "}\n")
                                            .build(filamentEngine)
                                }


                                if (materialPackage.isValid()) {
                                    val buffer = materialPackage.getBuffer()
                                    Material.builder()
                                            .setSource(buffer)
                                            .build()
                                            .thenAccept {material ->
                                                handler(model, null, material, null)
                                            }
                                            .exceptionally { throwable ->
                                                handler(null, null, null, throwable)
                                                Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                                                null
                                            }
                                }

                                MaterialBuilder.shutdown()
                            }

                            .exceptionally { throwable ->
                                handler(null, null, null, throwable)
                                Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                                null
                            }
                } else if (flutterArCoreNode.mediaInfo.isGif) {
                    // GIF
                    val gif = CustomGifView(context, flutterArCoreNode)
                    gif.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

                    ViewRenderable.builder().setView(context, gif)
                            .build()
                            .thenAccept(Consumer { renderable: ViewRenderable -> handler(renderable, null, null, null) })
                            .exceptionally { throwable ->
                                Log.e(TAG, "Unable to load mediaInfo renderable.", throwable)
                                handler(null, null, null, throwable)
                                return@exceptionally null
                            }
                } else {
                    // Static mediaInfo
                    val image = ImageView(context)
                    image.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
//                  val bmp = BitmapFactory.decodeByteArray(flutterArCoreNode.mediaInfo.bytes, 0, flutterArCoreNode.mediaInfo.bytes.size)
//
//                  mediaInfo.setImageBitmap(Bitmap.createScaledBitmap(bmp, flutterArCoreNode.mediaInfo.width,
//                  flutterArCoreNode.mediaInfo.height, false))


                    val url = URL(flutterArCoreNode.objectUrl)
                    val stream = url.openConnection().getInputStream()
                    val bmp = BitmapFactory.decodeStream(stream)
                    image.setImageBitmap(bmp)

                    ViewRenderable.builder().setView(context, image)
                            .build()
                            .thenAccept(Consumer { renderable: ViewRenderable -> handler(renderable, null, null, null) })
                            .exceptionally { throwable ->
                                Log.e(TAG, "Unable to load mediaInfo renderable.", throwable)
                                handler(null, null, null, throwable)
                                return@exceptionally null
                            }
                }
            } else {
                // Shapes
                makeMaterial(context, flutterArCoreNode) { material, throwable ->
                    if (throwable != null) {
                        handler(null, null, null, throwable)
                        return@makeMaterial
                    }
                    if (material == null) {
                        handler(null, null, null, null)
                        return@makeMaterial
                    }
                    try {
                        val renderable = flutterArCoreNode.shape?.buildShape(material)
                        handler(renderable, null, null, null)
                    } catch (ex: Exception) {
                        Log.i(TAG, "renderable error ${ex}")
                        handler(null, null, null, ex)
                        Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG)
                    }
                }
            }
        }

        private fun makeMaterial(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: MaterialHandler) {
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
                        handler(material, null)
                    }?.exceptionally { throwable ->
                        Log.i(TAG, "texture error ${throwable}")
                        handler(null, throwable)
                        return@exceptionally null
                    }
                }
            } else if (color != null) {
                MaterialCustomFactory.makeWithColor(context, flutterArCoreNode.shape.materials[0])
                        ?.thenAccept { material: Material ->
                            handler(material, null)
                        }?.exceptionally { throwable ->
                            Log.i(TAG, "material error ${throwable}")
                            handler(null, throwable)
                            return@exceptionally null
                        }
            } else {
                handler(null, null)
            }
        }
    }
}





//                    url = "https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"

// Textured static mediaInfo
/*
ModelRenderable.builder()
    .setSource(context, Uri.parse("models/cube.glb"))
    .setIsFilamentGltf(true)
    .build()
    .thenAccept { model ->

        Texture.builder()
            .setSampler(Texture.Sampler.builder()
                    .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
                    .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                    .setWrapMode(Texture.Sampler.WrapMode.MIRRORED_REPEAT)
                    .build())
            .setSource(context, Uri.parse("textures/rock.jpg"))
            .setUsage(Texture.Usage.COLOR)
            .build()
            .thenAccept { texture ->

                flutterArCoreNode.buildNode()

                handler(model, texture, null, null)
            }
            .exceptionally { throwable ->
                handler(null, null, null, throwable)
                Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                null
            }
    }

    .exceptionally { throwable ->
        handler(null, null, null, throwable)
        Log.i(TAG, "renderable error ${throwable.localizedMessage}")
        null
    }
    */





// Chroma textured video
/*
ModelRenderable.builder()
        .setSource(context, Uri.parse("models/vertical_plane_1920x1080.glb"))
        .setIsFilamentGltf(true)
        .build()
        .thenAccept { model ->

            val filamentEngine = EngineInstance.getEngine().getFilamentEngine();

            MaterialBuilder.init();
            val materialBuilder = MaterialBuilder()
                    // By default, materials are generated only for DESKTOP. Since we're an Android
                    // app, we set the platform to MOBILE.
                    .platform(MaterialBuilder.Platform.MOBILE)
                    .name("Plain Video Material")
                    .require(MaterialBuilder.VertexAttribute.UV0)
                    // Defaults to UNLIT because it's the only emissive one
                    .shading(MaterialBuilder.Shading.UNLIT)
                    .doubleSided(true)


                    .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
                    .optimization(MaterialBuilder.Optimization.NONE)

           val chromaKeyVideoMaterialPackage = materialBuilder
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "chromaKeyColor")
            .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
            .material(
                    "vec3 desaturate(vec3 color, float amount) {\n" +
                    "    // Convert color to grayscale using Luma formula:\n" +
                    "    // https://en.wikipedia.org/wiki/Luma_%28video%29\n" +
                    "    vec3 gray = vec3(dot(vec3(0.2126, 0.7152, 0.0722), color));\n" +
                    "    return vec3(mix(color, gray, amount));\n" +
                    "}\n" +
                    "void material(inout MaterialInputs material) {\n" +
                    "    prepareMaterial(material);\n" +
                    "    vec4 color = texture(materialParams_videoTexture, getUV0()).rgba;\n" +
                    "    vec3 keyColor = materialParams.chromaKeyColor.rgb;\n" +
                    "    float threshold = 0.675;\n" +
                    "    float slope = 0.2;\n" +
                    "    float distance = abs(length(abs(keyColor - color.rgb)));\n" +
                    "    float edge0 = threshold * (1.0 - slope);\n" +
                    "    float alpha = smoothstep(edge0, threshold, distance);\n" +
                    "    color.rgb = desaturate(color.rgb, 1.0 - (alpha * alpha * alpha));\n" +
                    "\n" +
                    "    material.baseColor.a = alpha;\n" +
                    "    material.baseColor.rgb = inverseTonemapSRGB(color.rgb);\n" +
                    "    material.baseColor.rgb *= material.baseColor.a;\n" +
                    "}\n")
            .build()

            if (chromaKeyVideoMaterialPackage.isValid()) {
                val buffer = chromaKeyVideoMaterialPackage.getBuffer();
                Material.builder()
                        .setSource(buffer)
                        .build()
                        .thenAccept {material ->
                            handler(model, null, material, null)
                        }
                        .exceptionally { throwable ->
                            handler(null, null, null, throwable)
                            Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                            null
                        }
            }

            MaterialBuilder.shutdown()
        }

        .exceptionally { throwable ->
            handler(null, null, null, throwable)
            Log.i(TAG, "renderable error ${throwable.localizedMessage}")
            null
        }
   */