package org.comon.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class Live2DGLSurfaceView(context: Context): GLSurfaceView(context) {

    // 제스처 모드
    var isZoomEnabled = false
    var isMoveEnabled = false

    // 드래그 관련 변수
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // 핀치 줌 감지기
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isZoomEnabled) return false
            
            val scaleFactor = detector.scaleFactor
            queueEvent {
                val manager = LAppMinimumLive2DManager.getInstance()
                val newScale = manager.modelScale * scaleFactor
                manager.setModelScale(newScale)
            }
            return true
        }
    })

    init {
        // OpenGL ES 2.0
        setEGLContextClientVersion(2)

        // Predictive back 제스처 등으로 GLSurfaceView가 pause/resume될 때
        // EGL 컨텍스트를 유지하여 GL 리소스(텍스처 등)가 무효화되지 않도록 함
        preserveEGLContextOnPause = true

        // 뷰 생성 시 델리게이트 초기화 (재진입 시 리소스 갱신 위해)
        LAppMinimumDelegate.getInstance().onStart(context as android.app.Activity)

        // Live2D minimum renderer
        setRenderer(GLRendererMinimum())

        // 계속 렌더링(테스트용)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 핀치 줌 처리
        if (isZoomEnabled && event.pointerCount >= 2) {
            scaleGestureDetector.onTouchEvent(event)
            return true
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                isDragging = false
                
                if (!isMoveEnabled) {
                    queueEvent {
                        LAppMinimumDelegate.getInstance().onTouchBegan(x, y)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMoveEnabled) {
                    // 이동 모드: 캐릭터 위치 이동
                    val deltaX = (x - lastTouchX) / width * 2f
                    val deltaY = -(y - lastTouchY) / height * 2f  // Y축 반전
                    
                    queueEvent {
                        val manager = LAppMinimumLive2DManager.getInstance()
                        manager.setModelOffset(
                            manager.modelOffsetX + deltaX,
                            manager.modelOffsetY + deltaY
                        )
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    isDragging = true
                } else {
                    queueEvent {
                        LAppMinimumDelegate.getInstance().onTouchMoved(x, y)
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!isMoveEnabled) {
                    queueEvent {
                        LAppMinimumDelegate.getInstance().onTouchEnd(x, y)
                    }
                }
                isDragging = false
            }
        }
        return true
    }

}
