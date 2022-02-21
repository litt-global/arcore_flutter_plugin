package com.difrancescogianmarco.arcore_flutter_plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreHitTestResult
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCorePose
import com.difrancescogianmarco.arcore_flutter_plugin.models.RotatingNode
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.rendering.*
import io.flutter.app.FlutterApplication
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import com.google.ar.sceneform.math.Vector3
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class ArCoreView(val activity: Activity, context: Context, messenger: BinaryMessenger, id: Int, private val isAugmentedFaces: Boolean) : PlatformView, MethodChannel.MethodCallHandler {
    private val methodChannel: MethodChannel = MethodChannel(messenger, "arcore_flutter_plugin_$id")
    //       private val activity: Activity = (context.applicationContext as FlutterApplication).currentActivity
    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks
    private var installRequested: Boolean = false
    private var mUserRequestedInstall = true
    private val TAG: String = ArCoreView::class.java.name
    private var arSceneView: ArSceneView? = null
    private val gestureDetector: GestureDetector
    private val RC_PERMISSIONS = 0x123
    private var sceneUpdateListener: Scene.OnUpdateListener
    private var faceSceneUpdateListener: Scene.OnUpdateListener
    private var mediaPlayers: MutableList<MediaPlayer> = mutableListOf()

    //AUGMENTEDFACE
    private var faceRegionsRenderable: ModelRenderable? = null
    private var faceMeshTexture: Texture? = null
//    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()

    init {
        methodChannel.setMethodCallHandler(this)
        arSceneView = ArSceneView(context)
        // Set up a tap gesture detector.
        gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        onSingleTap(e)
                        return true
                    }

                    override fun onDown(e: MotionEvent): Boolean {
                        return true
                    }
                })

        sceneUpdateListener = Scene.OnUpdateListener { frameTime ->

            val frame = arSceneView?.arFrame ?: return@OnUpdateListener

            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@OnUpdateListener
            }

            for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING) {

                    val pose = plane.centerPose
                    val map: HashMap<String, Any> = HashMap<String, Any>()
                    map["type"] = plane.type.ordinal
                    map["centerPose"] = FlutterArCorePose(pose.translation, pose.rotationQuaternion).toHashMap()
                    map["extentX"] = plane.extentX
                    map["extentZ"] = plane.extentZ

                    methodChannel.invokeMethod("onPlaneDetected", map)
                }
            }
        }

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
                //                if (faceRegionsRenderable == null || faceMeshTexture == null) {
                if (faceMeshTexture == null) {
                    return@OnUpdateListener
                }

                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    // Make new AugmentedFaceNodes for any new faces.
                    for (face in faceList) {
//                        if (!faceNodeMap.containsKey(face)) {
//                            val faceNode = AugmentedFaceNode(face)
//                            faceNode.setParent(arSceneView?.scene)
//                            faceNode.faceRegionsRenderable = faceRegionsRenderable
//                            faceNode.faceMeshTexture = faceMeshTexture
//                            faceNodeMap[face] = faceNode
//                        }
                    }

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
//                    val iter = faceNodeMap.iterator()
//                    while (iter.hasNext()) {
//                        val entry = iter.next()
//                        val face = entry.key
//                        if (face.trackingState == TrackingState.STOPPED) {
//                            val faceNode = entry.value
//                            faceNode.setParent(null)
//                            iter.remove()
//                        }
//                    }
                }
            }
        }

        // Lastly request CAMERA permission which is required by ARCore.
        ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
        setupLifeCycle(context)
    }

    fun loadMesh(textureBytes: ByteArray?) {
        // Load the face regions renderable.
        // This is a skinned model that renders 3D objects mapped to the regions of the augmented face.
        /*ModelRenderable.builder()
                .setSource(activity, Uri.parse("fox_face.sfb"))
                .build()
                .thenAccept { modelRenderable ->
                    faceRegionsRenderable = modelRenderable;
                    modelRenderable.isShadowCaster = false;
                    modelRenderable.isShadowReceiver = false;
                }*/

        // Load the face mesh texture.
        //                .setSource(activity, Uri.parse("fox_face_mesh_texture.png"))
        Texture.builder()
                .setSource(BitmapFactory.decodeByteArray(textureBytes, 0, textureBytes!!.size))
                .build()
                .thenAccept { texture -> faceMeshTexture = texture }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "init" -> {
                arScenViewInit(call, result, activity)
            }
            "addArCoreNode" -> {
                Log.i(TAG, " addArCoreNode")
                val map = call.arguments as HashMap<String, Any>
                val flutterNode = FlutterArCoreNode(map);
                onAddNode(flutterNode, result)
            }
            "addArCoreNodeWithAnchor" -> {
                Log.i(TAG, " addArCoreNode")
                val map = call.arguments as HashMap<String, Any>
                val flutterNode = FlutterArCoreNode(map)
                addNodeWithAnchor(flutterNode, result)
            }
            "removeARCoreNode" -> {
                Log.i(TAG, " removeARCoreNode")
                val map = call.arguments as HashMap<String, Any>
                removeNode(map["nodeName"] as String, result)
            }
            "positionChanged" -> {
                updatePosition(call, result)
            }
            "rotationChanged" -> {
                updateRotation(call, result)
            }
            "updateMaterials" -> {
                Log.i(TAG, " updateMaterials")
                updateMaterials(call, result)

            }
            "loadMesh" -> {
                val map = call.arguments as HashMap<String, Any>
                val textureBytes = map["textureBytes"] as ByteArray
                loadMesh(textureBytes)
            }
            "dispose" -> {
                Log.i(TAG, "Disposing ARCore now")
                dispose()
            }
            "resume" -> {
                Log.i(TAG, "Resuming ARCore now")
                onResume()
            }
            "getTrackingState" -> {
                // Log.i(TAG, "1/3: Requested tracking state, returning that back to Flutter now")
                val trState = arSceneView?.arFrame?.camera?.trackingState
                // Log.i(TAG, "2/3: Tracking state is " + trState.toString())
                methodChannel.invokeMethod("getTrackingState", trState.toString())
            }
            "getCameraPosition" -> {
                getCameraPosition(result)
            }
            "getCameraEulerAngles" -> {
                getCameraEulerAngles(result)
            }
            else -> {
            }
        }
    }

/*    fun maybeEnableArButton() {
        Log.i(TAG,"maybeEnableArButton" )
        try{
            val availability = ArCoreApk.getInstance().checkAvailability(activity.applicationContext)
            if (availability.isTransient) {
                // Re-query at 5Hz while compatibility is checked in the background.
                Handler().postDelayed({ maybeEnableArButton() }, 200)
            }
            if (availability.isSupported) {
                Log.i(TAG, "AR SUPPORTED")
            } else { // Unsupported or unknown.
                Log.i(TAG, "AR NOT SUPPORTED")
            }
        }catch (ex:Exception){
            Log.i(TAG,"maybeEnableArButton ${ex.localizedMessage}" )
        }

    }*/

    private fun setupLifeCycle(context: Context) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.i(TAG, "onActivityCreated")
//                maybeEnableArButton()
            }

            override fun onActivityStarted(activity: Activity) {
                Log.i(TAG, "onActivityStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                Log.i(TAG, "onActivityResumed")
                onResume()
            }

            override fun onActivityPaused(activity: Activity) {
                Log.i(TAG, "onActivityPaused")
                onPause()
            }

            override fun onActivityStopped(activity: Activity) {
                Log.i(TAG, "onActivityStopped (Just so you know)")
//                onPause()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                Log.i(TAG, "onActivityDestroyed (Just so you know)")
//                onDestroy()
//                dispose()
            }
        }

        activity.application.registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

    private fun onSingleTap(tap: MotionEvent?) {
        Log.i(TAG, " onSingleTap")
        val frame = arSceneView?.arFrame
        if (frame != null) {
            if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
                val hitList = frame.hitTest(tap)
                val list = ArrayList<HashMap<String, Any>>()
                for (hit in hitList) {
                    val trackable = hit.trackable
                    if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                        hit.hitPose
                        val distance: Float = hit.distance
                        val translation = hit.hitPose.translation
                        val rotation = hit.hitPose.rotationQuaternion
                        val flutterArCoreHitTestResult = FlutterArCoreHitTestResult(distance, translation, rotation)
                        val arguments = flutterArCoreHitTestResult.toHashMap()
                        list.add(arguments)
                    }
                }
                methodChannel.invokeMethod("onPlaneTap", list)
            }
        }
    }

    private fun arScenViewInit(call: MethodCall, result: MethodChannel.Result, context: Context) {
        Log.i(TAG, "arScenViewInit")
        val enableTapRecognizer: Boolean? = call.argument("enableTapRecognizer")
        if (enableTapRecognizer != null && enableTapRecognizer) {
            arSceneView
                    ?.scene
                    ?.setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent? ->

                        if (hitTestResult.node != null) {
                            Log.i(TAG, " onNodeTap " + hitTestResult.node?.name)
                            Log.i(TAG, hitTestResult.node?.localPosition.toString())
                            Log.i(TAG, hitTestResult.node?.worldPosition.toString())
                            methodChannel.invokeMethod("onNodeTap", hitTestResult.node?.name)
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener gestureDetector.onTouchEvent(event)
                    }
        }
        val enableUpdateListener: Boolean? = call.argument("enableUpdateListener")
        if (enableUpdateListener != null && enableUpdateListener) {
            // Set an update listener on the Scene that will hide the loading message once a Plane is
            // detected.
            arSceneView?.scene?.addOnUpdateListener(sceneUpdateListener)
        }

        val enablePlaneRenderer: Boolean? = call.argument("enablePlaneRenderer")
        if (enablePlaneRenderer != null && !enablePlaneRenderer) {
            Log.i(TAG, " The plane renderer (enablePlaneRenderer) is set to " + enablePlaneRenderer.toString())
            arSceneView!!.planeRenderer.isVisible = false
        }
        
        result.success(null)
    }

    fun addNodeWithAnchor(flutterArCoreNode: FlutterArCoreNode, result: MethodChannel.Result) {

        if (arSceneView == null) {
            return
        }

        RenderableCustomFactory.makeRenderable(activity.applicationContext, flutterArCoreNode) { renderable, texture, material, t ->
            if (t != null) {
                result.error("Make Renderable Error", t.localizedMessage, null)
                return@makeRenderable
            }
            val myAnchor = arSceneView?.session?.createAnchor(Pose(flutterArCoreNode.getPosition(), flutterArCoreNode.getRotation()))
            if (myAnchor != null) {
                val anchorNode = flutterArCoreNode.buildNode() //AnchorNode(myAnchor)
                anchorNode.name = flutterArCoreNode.name
                anchorNode.renderable = renderable

                if (texture != null) {
                    anchorNode.renderableInstance!!.material.setInt("baseColorIndex", 0)
                    anchorNode.renderableInstance!!.material.setTexture("baseColorMap", texture)
                }

                if (material != null) {
                    anchorNode.renderableInstance!!.setMaterial(material)
                }

                if (flutterArCoreNode.mediaInfo?.isVideo == true) {
                    if (flutterArCoreNode.shape == null) {
                        anchorNode.localScale = Vector3(200 / 1920f, 200 / 1080f, 1f)
                    }

                    if (flutterArCoreNode.mediaInfo?.chromaColor != null) {
                        val chroma = flutterArCoreNode.mediaInfo.chromaColor
                        val r = ((chroma and 0xFF0000) shr 16) / 255F
                        val g = ((chroma and 0xFF00) shr 8) / 255F
                        val b = (chroma and 0xFF) / 255F

                        anchorNode.renderableInstance!!.getMaterial().setFloat4("chromaKeyColor", Color(r, g, b))
                    }

                    thread {
                        val externalTexture = ExternalTexture()
                        val volume = if (flutterArCoreNode.mediaInfo.isMuted) 0.0f else 1.0f
                        val mediaPlayer = MediaPlayer.create(activity.applicationContext, Uri.parse(flutterArCoreNode.objectUrl))
                        mediaPlayer.isLooping = true
                        mediaPlayer.setSurface(externalTexture.surface)
                        mediaPlayer.setVolume(volume, volume)
                        anchorNode.renderableInstance!!.material.setExternalTexture("videoTexture", externalTexture)
                        if (flutterArCoreNode.shape == null) {
                            anchorNode.localScale = Vector3(mediaPlayer.videoWidth / 1920f, mediaPlayer.videoHeight / 1080f, 1f)
                        }
                        mediaPlayer.start()
                        mediaPlayers.add(mediaPlayer)
                    }
                }

                if (flutterArCoreNode.scale != null) {
                    anchorNode.localScale = Vector3(
                            anchorNode.localScale.x * flutterArCoreNode.scale.x,
                            anchorNode.localScale.y * flutterArCoreNode.scale.y ,
                            anchorNode.localScale.z * flutterArCoreNode.scale.z)
                }

                if (flutterArCoreNode.mediaInfo == null || flutterArCoreNode.mediaInfo.isGif) {
                    anchorNode.renderableInstance!!.animate(true).start()
                }

                Log.i(TAG, "addNodeWithAnchor inserted ${anchorNode.name}")
                attachNodeToParent(anchorNode, flutterArCoreNode.parentNodeName)

                for (node in flutterArCoreNode.children) {
                    node.parentNodeName = flutterArCoreNode.name

                    onAddNode(node, null)
                }
            }
            result.success(null)
        }
    }

    fun onAddNode(flutterArCoreNode: FlutterArCoreNode, result: MethodChannel.Result?) {

        Log.i(TAG, flutterArCoreNode.toString())
        NodeFactory.makeNode(activity.applicationContext, flutterArCoreNode) { node, throwable ->

            Log.i(TAG, "onAddNode inserted ${node?.name}")

/*            if (flutterArCoreNode.parentNodeName != null) {
                Log.i(TAG, flutterArCoreNode.parentNodeName);
                val parentNode: Node? = arSceneView?.scene?.findByName(flutterArCoreNode.parentNodeName)
                parentNode?.addChild(node)
            } else {
                Log.i(TAG, "addNodeToSceneWithGeometry: NOT PARENT_NODE_NAME")
                arSceneView?.scene?.addChild(node)
            }*/
            if (node != null) {
                attachNodeToParent(node, flutterArCoreNode.parentNodeName)
                for (n in flutterArCoreNode.children) {
                    n.parentNodeName = flutterArCoreNode.name
                    onAddNode(n, null)
                }
            }

        }
        result?.success(null)
    }

    fun attachNodeToParent(node: Node?, parentNodeName: String?) {
        if (parentNodeName != null) {
            Log.i(TAG, parentNodeName);
            val parentNode: Node? = arSceneView?.scene?.findByName(parentNodeName)
            parentNode?.addChild(node)
        } else {
            Log.i(TAG, "addNodeToSceneWithGeometry: NOT PARENT_NODE_NAME")
            arSceneView?.scene?.addChild(node)
        }
    }

    fun removeNode(name: String, result: MethodChannel.Result) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            arSceneView?.scene?.removeChild(node);
            Log.i(TAG, "removed ${node.name}")
        }

        result.success(null)
    }

    fun updatePosition(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val node = arSceneView?.scene?.findByName(name)

        val x = call.argument<Double?>("x")
        val y = call.argument<Double?>("y")
        val z = call.argument<Double?>("z")

        if (x != null && y != null && z != null) {
            node?.localPosition = Vector3(x.toFloat(), y.toFloat(), z.toFloat())
        }

        result.success(null)
    }

    fun updateRotation(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val node = arSceneView?.scene?.findByName(name) as RotatingNode
        Log.i(TAG, "rotating node:  $node")
        val degreesPerSecond = call.argument<Double?>("degreesPerSecond")
        Log.i(TAG, "rotating value:  $degreesPerSecond")
        if (degreesPerSecond != null) {
            Log.i(TAG, "rotating value:  ${node.degreesPerSecond}")
            node.degreesPerSecond = degreesPerSecond.toFloat()
        }
        result.success(null)
    }

    fun updateMaterials(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val materials = call.argument<ArrayList<HashMap<String, *>>>("materials")!!
        val node = arSceneView?.scene?.findByName(name)
        val oldMaterial = node?.renderable?.material?.makeCopy()
        if (oldMaterial != null) {
            val material = MaterialCustomFactory.updateMaterial(oldMaterial, materials[0])
            node.renderable?.material = material
        }
        result.success(null)
    }

    fun getCameraPosition(result: MethodChannel.Result) {
        val position = arSceneView?.scene?.camera?.worldPosition;
        if (position == null) {
            result.success(null)
        } else {
            result.success(arrayOf(position.x, position.y, position.z).toList())
        }
    }

    fun getCameraEulerAngles(result: MethodChannel.Result) {
        val angles = Vector3()
        val forward = arSceneView?.scene?.camera?.forward
        val q = arSceneView?.scene?.camera?.worldRotation

        if (q == null || forward == null) {
            result.success(null)
            return
        }

        // roll (x-axis rotation)
        val sinr_cosp = 2.0 * (q.w * q.x + q.y * q.z)
        val cosr_cosp = 1 - 2.0 * (q.x * q.x + q.y * q.y)
        angles.x = Math.atan2(sinr_cosp, cosr_cosp).toFloat()

        // pitch (y-axis rotation)
        val sinp = 2.0 * (q.w * q.y - q.z * q.x)
        if (Math.abs(sinp) >= 1)
            angles.y = Math.copySign(Math.PI / 2, sinp).toFloat() // use 90 degrees if out of range
        else
            angles.y = Math.asin(sinp).toFloat()

        if (forward.z > 0) {
            if (angles.y > 0) {
                angles.y = (Math.PI - angles.y).toFloat()
            } else {
                angles.y = (-Math.PI - angles.y).toFloat()
            }
        }

        // yaw (z-axis rotation)
        val siny_cosp = 2.0 * (q.w * q.z + q.x * q.y)
        val cosy_cosp = 1 - 2.0 * (q.y * q.y + q.z * q.z)
        angles.z = Math.atan2(siny_cosp, cosy_cosp).toFloat()

        result.success(arrayOf(angles.x, angles.y, angles.z).toList())
    }

    override fun getView(): View {
        if(arSceneView != null){
            return arSceneView as View
        }
        return View(activity.applicationContext)
    }

    override fun dispose() {
        for (mediaPlayer in mediaPlayers) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }

        if (arSceneView != null) {
            onPause()
            onDestroy()
        }

        methodChannel.setMethodCallHandler(null)
    }

    fun onResume() {
        Log.i(TAG, "onResume()")

        if (arSceneView == null) {
            return
        }

        // request camera permission if not already requested
        if (!ArCoreUtils.hasCameraPermission(activity)) {
            ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
        }

        if (arSceneView?.session == null) {
            Log.i(TAG, "session is null")
            try {
                val session = ArCoreUtils.createArSession(activity, mUserRequestedInstall, isAugmentedFaces)
                if (session == null) {
                    // Ensures next invocation of requestInstall() will either return
                    // INSTALLED or throw an exception.
                    mUserRequestedInstall = false
                    return
                } else {
                    val config = Config(session)
                    if (isAugmentedFaces) {
                        config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    }
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    config.focusMode = Config.FocusMode.AUTO;
                    session.configure(config)
                    arSceneView?.setupSession(session)
                }
            } catch (ex: UnavailableUserDeclinedInstallationException) {
                // Display an appropriate message to the user zand return gracefully.
                Toast.makeText(activity, "TODO: handle exception " + ex.localizedMessage, Toast.LENGTH_LONG)
                        .show();
                return
            } catch (e: UnavailableException) {
                ArCoreUtils.handleSessionException(activity, e)
                return
            }
        }

        try {
            arSceneView?.resume()
        } catch (ex: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", ex)
            activity.finish()
            return
        }

        if (arSceneView?.session != null) {
            //arSceneView!!.planeRenderer.isVisible = false
            Log.i(TAG, "Searching for surfaces")
        }
    }

    fun onPause() {
        if (arSceneView != null) {
            arSceneView?.pause()
        }
    }

    fun onDestroy() {
      if (arSceneView != null) {
            Log.i(TAG, "Goodbye ARCore! Destroying the Activity now 7.")

            try {
                arSceneView?.scene?.removeOnUpdateListener(sceneUpdateListener)
                arSceneView?.scene?.removeOnUpdateListener(faceSceneUpdateListener)
                Log.i(TAG, "Goodbye arSceneView.")

                arSceneView?.destroy()
                arSceneView = null

            }catch (e : Exception){
                e.printStackTrace();
           }
        }
    }

    /* private fun tryPlaceNode(tap: MotionEvent?, frame: Frame) {
        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    // Create the Anchor.
                    val anchor = hit.createAnchor()
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arSceneView?.scene)

                    ModelRenderable.builder()
                            .setSource(activity.applicationContext, Uri.parse("TocoToucan.sfb"))
                            .build()
                            .thenAccept { renderable ->
                                val node = Node()
                                node.renderable = renderable
                                anchorNode.addChild(node)
                            }.exceptionally { throwable ->
                                Log.e(TAG, "Unable to load Renderable.", throwable);
                                return@exceptionally null
                            }
                }
            }
        }

    }*/

    /*    fun updatePosition(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val node = arSceneView?.scene?.findByName(name)
        node?.localPosition = parseVector3(call.arguments as HashMap<String, Any>)
        result.success(null)
    }*/
}