package org.comon.live2d;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GL 프레임버퍼를 읽어 MediaRecorder의 Surface로 전달하는 유틸리티.
 *
 * <p>{@code glReadPixels}를 사용하여 현재 GL 프레임버퍼 내용을 읽고,
 * {@link Surface#lockHardwareCanvas()}를 통해 인코더 Surface에 그립니다.</p>
 *
 * <p>EGL 컨텍스트 공유 방식 대비 GPU-CPU 왕복이 발생하지만,
 * 모든 디바이스에서 안정적으로 동작합니다.</p>
 *
 * <p>모든 메서드는 GL 스레드에서 호출해야 합니다.</p>
 */
public class EglRecordHelper {

    private static final String TAG = "EglRecordHelper";

    private Surface recordingSurface;
    private ByteBuffer pixelBuffer;
    private Bitmap bitmap;
    private Matrix flipMatrix;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean initialized = false;

    /**
     * 프레임 캡처에 필요한 버퍼와 비트맵을 초기화합니다.
     * GL 스레드의 {@code onSurfaceChanged()}에서 호출하세요.
     *
     * @param width  Surface 너비
     * @param height Surface 높이
     */
    public void init(int width, int height) {
        release();

        surfaceWidth = width;
        surfaceHeight = height;

        // glReadPixels용 네이티브 바이트 버퍼 (RGBA, 4bytes/pixel)
        pixelBuffer = ByteBuffer.allocateDirect(width * height * 4);
        pixelBuffer.order(ByteOrder.nativeOrder());

        // 프레임 전달용 비트맵
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // GL은 좌하단 원점, Canvas는 좌상단 원점 → 수직 반전 매트릭스
        flipMatrix = new Matrix();
        flipMatrix.postScale(1, -1, width / 2f, height / 2f);

        initialized = true;
        Log.d(TAG, "Initialized: " + width + "x" + height);
    }

    /**
     * MediaRecorder의 입력 Surface를 설정합니다.
     * GL 스레드에서 호출하세요.
     *
     * @param surface 녹화용 Surface. null이면 녹화를 중단합니다.
     */
    public void setRecordingSurface(Surface surface) {
        Log.d(TAG, "setRecordingSurface: " + surface + ", initialized=" + initialized);
        recordingSurface = surface;
    }

    /**
     * 현재 GL 프레임버퍼 내용을 인코더 Surface에 전달합니다.
     * GL 스레드의 {@code onDrawFrame()} 끝에서 호출하세요.
     */
    public void onFrameAvailable() {
        if (!isRecording()) return;

        try {
            // 1) GL 프레임버퍼에서 픽셀 읽기 (GPU → CPU)
            pixelBuffer.rewind();
            GLES20.glReadPixels(0, 0, surfaceWidth, surfaceHeight,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);

            // 2) ByteBuffer → Bitmap 변환
            pixelBuffer.rewind();
            bitmap.copyPixelsFromBuffer(pixelBuffer);

            // 3) MediaRecorder Surface에 그리기 (수직 반전 포함)
            Canvas canvas = recordingSurface.lockHardwareCanvas();
            try {
                canvas.drawBitmap(bitmap, flipMatrix, null);
            } finally {
                recordingSurface.unlockCanvasAndPost(canvas);
            }
        } catch (IllegalStateException e) {
            // Surface가 이미 해제되었거나 유효하지 않은 경우
            Log.w(TAG, "Surface no longer valid, stopping frame capture", e);
            recordingSurface = null;
        } catch (Exception e) {
            Log.e(TAG, "Error in onFrameAvailable", e);
        }
    }

    /**
     * 녹화가 활성화되어 있는지 확인합니다.
     */
    public boolean isRecording() {
        return initialized && recordingSurface != null && recordingSurface.isValid();
    }

    /**
     * 현재 Surface 너비를 반환합니다.
     */
    public int getWidth() {
        return surfaceWidth;
    }

    /**
     * 현재 Surface 높이를 반환합니다.
     */
    public int getHeight() {
        return surfaceHeight;
    }

    /**
     * 모든 리소스를 해제합니다. GL 스레드에서 호출하세요.
     */
    public void release() {
        recordingSurface = null;
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        pixelBuffer = null;
        flipMatrix = null;
        initialized = false;
    }
}
