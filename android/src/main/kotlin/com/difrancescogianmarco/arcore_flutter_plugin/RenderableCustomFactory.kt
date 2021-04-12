package com.difrancescogianmarco.arcore_flutter_plugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.Toast
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.google.ar.sceneform.assets.RenderableSource
import java.util.function.Consumer

import android.widget.RelativeLayout.LayoutParams;
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*


typealias MaterialHandler = (Material?, Throwable?) -> Unit
typealias RenderableHandler = (Renderable?, Throwable?) -> Unit

class RenderableCustomFactory {

    companion object {

        val TAG = "RenderableCustomFactory"
        private var videoRenderable: ModelRenderable? = null
        private var mediaPlayer: MediaPlayer? = null
        // The color to filter out of the video.
        private val CHROMA_KEY_COLOR = Color(0.1843f, 1.0f, 0.098f)

        // Controls the height of the video in world space.
        private val VIDEO_HEIGHT_METERS = 0.85f

        @SuppressLint("ShowToast")
        fun makeRenderable(context: Context, flutterArCoreNode: FlutterArCoreNode, handler: RenderableHandler) {

            if (flutterArCoreNode.dartType == "ArCoreReferenceNode") {

                val url = flutterArCoreNode.objectUrl

                val localObject = flutterArCoreNode.object3DFileName
                if (localObject != null) {
                    val builder = ModelRenderable.builder()
                    builder.setSource(context, Uri.parse(localObject))
                    builder.build().thenAccept { renderable ->
                        handler(renderable, null)
                    }.exceptionally { throwable ->
                        Log.e(TAG, "Unable to load Renderable.", throwable);
                        handler(null, throwable)
                        return@exceptionally null
                    }
                } else if (url != null) {
                    val modelRenderableBuilder = ModelRenderable.builder()
                    val renderableSourceBuilder = RenderableSource.builder()
                    if(url.endsWith(".glb")){
//                        val uri = Uri.parse("android.resource://"+context.packageName+"/" + R.raw.planemesh)
                        renderableSourceBuilder
                            .setSource(context, Uri.parse(url), RenderableSource.SourceType.GLB)
                            .setScale(0.5f)
                            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                    } else {
                        renderableSourceBuilder
                            .setSource(context, Uri.parse(url), RenderableSource.SourceType.GLTF2)
                            .setScale(0.5f)
                            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                    }
//                    val uri2 = Uri.parse("android.resource://"+context.packageName+"/" + R.raw.planemesh2)
                    renderableSourceBuilder
                            .setSource(context, Uri.parse("https://raw.githubusercontent.com/BrutalCoding/cashbag/master/quad_plane_mesh.glb"), RenderableSource.SourceType.GLB)
                            .setScale(0.5f)
                            .setRecenterMode(RenderableSource.RecenterMode.ROOT)

                    Log.d("Daniel", "Reached ARCoreee level 1: " + context.packageName)

                    // Create an ExternalTexture for displaying the contents of the video.
                    val texture = ExternalTexture()

                    // Create an Android MediaPlayer to capture the video on the external texture's surface.
                    val uri = Uri.parse("android.resource://" + context.packageName+"/" + R.raw.littuser)
//                    mediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
//                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
//                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
//                            .setUsage(AudioAttributes.USAGE_ALARM)
//                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                            .build())
                    mediaPlayer = MediaPlayer.create(context, uri)

//                    mediaPlayer = MediaPlayer.create(context, R.raw.littuser)
                    mediaPlayer!!.isLooping = true
                    mediaPlayer!!.setSurface(texture.surface)


//                    val uriObj = Uri.parse("android.resource://"+context.packageName+"/" + R.raw.chroma_key_video_plugin)
                    modelRenderableBuilder
                            .setSource(context, renderableSourceBuilder.build()) // 3d cash bag
//                            .setRegistryId(url)
//                            .setSource(context, R.raw.planemesh2)
                            .build()
                            .thenAccept { renderable ->
                                videoRenderable = renderable
                                renderable.material.setExternalTexture("videoTexture", texture)
//                                renderable.material.setFloat4("keyColor", CHROMA_KEY_COLOR)
                                handler(renderable, null)
                            }
                            .exceptionally { throwable ->
                                handler(null, throwable)
                                Log.i(TAG, "renderable error ${throwable.localizedMessage}")
                                val toast = Toast.makeText(context, "Unable to load video renderable", Toast.LENGTH_LONG)
                                toast.setGravity(Gravity.CENTER, 0, 0)
                                toast.show()
                                null
                            }
                    if (videoRenderable == null) {
                        return
                    }
                    Log.d("Daniel", "Reached ARCore level 2")

                    // Create the Anchor.
//                    val anchor = hitResult.createAnchor()
//                    val anchorNode = AnchorNode(anchor)
//                    anchorNode.setParent(arFragment.getArSceneView().getScene())

                    // Create a node to render the video and add it to the anchor.
                    val videoNode = Node()
                    videoNode.setParent(flutterArCoreNode.buildNode())
                    Log.d("Daniel", "Reached ARCore level 3")
                    // Set the scale of the node so that the aspect ratio of the video is correct.
                    val videoWidth = mediaPlayer!!.videoWidth.toFloat()
                    val videoHeight = mediaPlayer!!.videoHeight.toFloat()
                    videoNode.localScale = Vector3(
                            VIDEO_HEIGHT_METERS * (videoWidth / videoHeight), VIDEO_HEIGHT_METERS, 1.0f)
                    Log.d("Daniel", "Reached ARCore level 4")

//                     Start playing the video when the first node is placed.
                    if (!mediaPlayer!!.isPlaying) {
                        Log.d("Daniel", "Reached ARCore level 5 (1)")
                        mediaPlayer!!.start()

                        // Wait to set the renderable until the first frame of the  video becomes available.
                        // This prevents the renderable from briefly appearing as a black quad before the video
                        // plays.
                        texture
                                .surfaceTexture
                                .setOnFrameAvailableListener { surfaceTexture: SurfaceTexture ->
                                    videoNode.renderable = videoRenderable
                                    texture.surfaceTexture.setOnFrameAvailableListener(null)
                                }
                        Log.d("Daniel", "Reached ARCore level 6")
                    } else {
                        Log.d("Daniel", "Reached ARCore level 5 (2)")
                        videoNode.renderable = videoRenderable
                        Log.d("Daniel", "Reached ARCore level 6")
                    }
//                    handler(videoRenderable, null) //didnt work
                }

            } else {

                if (flutterArCoreNode.image != null) {
                    val image = ImageView(context);
                    image.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val bmp = BitmapFactory.decodeByteArray(flutterArCoreNode.image.bytes, 0, flutterArCoreNode.image.bytes.size)

                    image.setImageBitmap(Bitmap.createScaledBitmap(bmp, flutterArCoreNode.image.width,
                            flutterArCoreNode.image.height, false))

                    ViewRenderable.builder().setView(context, image)
                            .build()
                            .thenAccept(Consumer { renderable: ViewRenderable -> handler(renderable, null) })
                            .exceptionally { throwable ->
                                Log.e(TAG, "Unable to load image renderable.", throwable);
                                handler(null, throwable)
                                return@exceptionally null
                            }
                } else {
                    makeMaterial(context, flutterArCoreNode) { material, throwable ->
                        if (throwable != null) {
                            handler(null, throwable)
                            return@makeMaterial
                        }
                        if (material == null) {
                            handler(null, null)
                            return@makeMaterial
                        }
                        try {
                            val renderable = flutterArCoreNode.shape?.buildShape(material)
                            handler(renderable, null)
                        } catch (ex: Exception) {
                            Log.i(TAG, "renderable error ${ex}")
                            handler(null, ex)
                            Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG)
                        }
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

                val builder = com.google.ar.sceneform.rendering.Texture.builder();
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