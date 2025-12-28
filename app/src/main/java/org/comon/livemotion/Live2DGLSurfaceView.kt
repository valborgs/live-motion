package org.comon.livemotion

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import org.comon.livemotion.demo.minimum.GLRendererMinimum
import org.comon.livemotion.demo.minimum.LAppMinimumDelegate

class Live2DGLSurfaceView(context: Context): GLSurfaceView(context) {

    init {
        // OpenGL ES 2.0
        setEGLContextClientVersion(2)

        // Live2D minimum renderer
        setRenderer(GLRendererMinimum())

        // 계속 렌더링(테스트용)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        queueEvent {
            val delegate = LAppMinimumDelegate.getInstance()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    delegate.onTouchBegan(x, y)

                MotionEvent.ACTION_MOVE ->
                    delegate.onTouchMoved(x, y)

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL ->
                    delegate.onTouchEnd(x, y)
            }
        }
        return true
    }
}