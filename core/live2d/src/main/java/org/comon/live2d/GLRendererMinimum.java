/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package org.comon.live2d;

import android.opengl.GLSurfaceView;
import android.view.Surface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRendererMinimum implements GLSurfaceView.Renderer {

    private final EglRecordHelper recordHelper = new EglRecordHelper();

    // Called at initialization (when the drawing context is lost and recreated).
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        LAppMinimumDelegate.getInstance().onSurfaceCreated();
    }

    // Mainly called when switching between landscape and portrait.
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        LAppMinimumDelegate.getInstance().onSurfaceChanged(width, height);
        recordHelper.init(width, height);
    }

    // Called repeatedly for drawing.
    @Override
    public void onDrawFrame(GL10 unused) {
        LAppMinimumDelegate.getInstance().run();

        // 녹화 활성화 시 현재 프레임을 인코더 surface에도 렌더링
        if (recordHelper.isRecording()) {
            recordHelper.onFrameAvailable();
        }
    }

    /**
     * 녹화용 Surface를 설정합니다. GL 스레드에서 호출하세요.
     *
     * @param surface MediaRecorder의 입력 Surface. null이면 녹화를 중단합니다.
     */
    public void setRecordingSurface(Surface surface) {
        recordHelper.setRecordingSurface(surface);
    }

    /**
     * EglRecordHelper의 현재 Surface 너비를 반환합니다.
     */
    public int getRecordWidth() {
        return recordHelper.getWidth();
    }

    /**
     * EglRecordHelper의 현재 Surface 높이를 반환합니다.
     */
    public int getRecordHeight() {
        return recordHelper.getHeight();
    }

}
