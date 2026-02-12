package org.comon.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface

class Live2DGLSurfaceView(context: Context): GLSurfaceView(context) {

    // 제스처 모드
    var isZoomEnabled = false
    var isMoveEnabled = false

    // 렌더러 참조 (녹화용)
    private val glRenderer = GLRendererMinimum()

    /** GL Surface 너비 (녹화 해상도 결정용) */
    val surfaceWidth: Int get() = glRenderer.recordWidth

    /** GL Surface 높이 (녹화 해상도 결정용) */
    val surfaceHeight: Int get() = glRenderer.recordHeight

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
        setRenderer(glRenderer)

        // 계속 렌더링(테스트용)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * 녹화용 Surface를 설정합니다.
     * GL 스레드에서 렌더러에 전달됩니다.
     *
     * @param surface MediaRecorder의 입력 Surface. null이면 녹화를 중단합니다.
     */
    fun setRecordingSurface(surface: Surface?) {
        queueEvent {
            glRenderer.setRecordingSurface(surface)
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 핀치 줌 감지기에 항상 이벤트 전달 (줌 활성화 시)
        if (isZoomEnabled) {
            scaleGestureDetector.onTouchEvent(event)
        }

        // 핀치 줌 진행 중에는 드래그 처리 차단
        if (scaleGestureDetector.isInProgress) {
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

            MotionEvent.ACTION_POINTER_UP -> {
                // 핀치에서 한 손가락 뗄 때, 남은 손가락 위치로 갱신하여 튀는 현상 방지
                if (event.pointerCount > 1) {
                    val remainingPointerIndex = if (event.actionIndex == 0) 1 else 0
                    lastTouchX = event.getX(remainingPointerIndex)
                    lastTouchY = event.getY(remainingPointerIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // 두 손가락 이상일 때는 이동 처리 안함 (핀치 줌 전용)
                if (event.pointerCount >= 2) {
                    return true
                }

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

            MotionEvent.ACTION_UP -> {
                if (!isMoveEnabled) {
                    queueEvent {
                        LAppMinimumDelegate.getInstance().onTouchEnd(x, y)
                    }
                }
                if (!isDragging) {
                    performClick()
                }
                isDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

}
