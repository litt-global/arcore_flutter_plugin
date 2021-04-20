package com.difrancescogianmarco.arcore_flutter_plugin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.view.View
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import java.net.URL

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