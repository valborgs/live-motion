/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package org.comon.live2d;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import com.live2d.sdk.cubism.framework.CubismFramework;

import static android.opengl.GLES20.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.comon.live2d.LAppDefine;

public class LAppMinimumDelegate {
    public static LAppMinimumDelegate getInstance() {
        if (s_instance == null) {
            s_instance = new LAppMinimumDelegate();
        }
        return s_instance;
    }

    /**
     * クラスのインスタンス（シングルトン）を解放する。
     */
    public static void releaseInstance() {
        if (s_instance != null) {
            s_instance = null;
        }
    }

    public void onStart(Activity activity) {
        textureManager = new LAppMinimumTextureManager();
        view = new LAppMinimumView();

        LAppMinimumPal.updateTime();
        this.activity = activity;
    }

    public void onStop() {
        // 배경 텍스처 정리
        clearBackgroundImage();

        // View와 TextureManager 정리
        if (view != null) {
            view.close();
            view = null;
        }
        textureManager = null;

        // 모델 매니저 정리 (모델 리소스 해제)
        LAppMinimumLive2DManager.releaseInstance();

        // CubismFramework 정리 (다음 초기화를 위해)
        CubismFramework.dispose();
    }

    public void onDestroy() {
        if (view != null) {
            view.close();
        }
        textureManager = null;

        LAppMinimumLive2DManager.releaseInstance();
        CubismFramework.dispose();
        releaseInstance();
    }

    public void onSurfaceCreated() {
        // テクスチャサンプリング設定
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        // 透過設定
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Initialize Cubism SDK framework
        CubismFramework.initialize();
    }

    public void onSurfaceChanged(int width, int height) {
        // 描画範囲指定
        GLES20.glViewport(0, 0, width, height);
        windowWidth = width;
        windowHeight = height;

        // AppViewの初期化
        view.initialize();
        view.initializeSprite();
    }

    public void run() {
        // 時間更新
        LAppMinimumPal.updateTime();

        // 画面初期化
        glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3]);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearDepthf(1.0f);

        // 배경 이미지 렌더링 (center-crop 방식)
        if (backgroundTextureId > 0 && backgroundSprite != null
                && backgroundImageWidth > 0 && backgroundImageHeight > 0) {
            // Center-crop: 화면을 완전히 채우면서 비율 유지, 넘치는 부분을 균등하게 잘라냄
            float screenAspect = (float) windowWidth / windowHeight;
            float imageAspect = (float) backgroundImageWidth / backgroundImageHeight;

            float uvLeft = 0f, uvRight = 1f, uvTop = 0f, uvBottom = 1f;
            if (imageAspect > screenAspect) {
                // 이미지가 화면보다 가로로 넓음 → 좌우를 잘라냄
                float visibleFraction = screenAspect / imageAspect;
                float offset = (1f - visibleFraction) / 2f;
                uvLeft = offset;
                uvRight = 1f - offset;
            } else if (imageAspect < screenAspect) {
                // 이미지가 화면보다 세로로 긺 → 상하를 잘라냄
                float visibleFraction = imageAspect / screenAspect;
                float offset = (1f - visibleFraction) / 2f;
                uvTop = offset;
                uvBottom = 1f - offset;
            }

            final float[] uvVertex = {
                uvRight, uvTop,
                uvLeft,  uvTop,
                uvLeft,  uvBottom,
                uvRight, uvBottom
            };
            backgroundSprite.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            backgroundSprite.setWindowSize(windowWidth, windowHeight);
            backgroundSprite.renderImmediate(backgroundTextureId, uvVertex);
        }

        if (view != null) {
            view.render();
        }
    }

    public void setBackgroundColor(float r, float g, float b, float a) {
        backgroundColor[0] = r;
        backgroundColor[1] = g;
        backgroundColor[2] = b;
        backgroundColor[3] = a;
    }

    public void setBackgroundImage(String filePath) {
        // 이전 배경 텍스처 해제
        clearBackgroundImage();

        if (filePath == null) {
            return;
        }

        try {
            InputStream stream;
            if (filePath.startsWith("/")) {
                // 절대 경로 (외부 배경)
                File file = new File(filePath);
                if (!file.exists()) return;
                stream = new FileInputStream(file);
            } else {
                // Asset 경로
                stream = activity.getAssets().open(filePath);
            }

            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            stream.close();
            if (bitmap == null) return;

            // GL 텍스처 생성
            int[] textureId = new int[1];
            GLES20.glGenTextures(1, textureId, 0);
            GLES20.glBindTexture(GL_TEXTURE_2D, textureId[0]);
            GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0);

            backgroundTextureId = textureId[0];
            backgroundImageWidth = bitmap.getWidth();
            backgroundImageHeight = bitmap.getHeight();
            bitmap.recycle();

            // 배경 스프라이트 생성 (화면 전체 크기)
            if (view != null && view.getSpriteShader() != null) {
                float x = windowWidth * 0.5f;
                float y = windowHeight * 0.5f;
                backgroundSprite = new LAppMinimumSprite(
                    x, y, windowWidth, windowHeight,
                    backgroundTextureId,
                    view.getSpriteShader().getShaderId()
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearBackgroundImage() {
        if (backgroundTextureId > 0) {
            int[] textures = {backgroundTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            backgroundTextureId = -1;
        }
        backgroundSprite = null;
    }

    public void onTouchBegan(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = true;
            view.onTouchesBegan(mouseX, mouseY);
        }
    }

    public void onTouchEnd(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = false;
            view.onTouchesEnded(mouseX, mouseY);
        }
    }

    public void onTouchMoved(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (isCaptured && view != null) {
            view.onTouchesMoved(mouseX, mouseY);
        }
    }

    // getter, setter群
    public LAppMinimumTextureManager getTextureManager() {
        return textureManager;
    }

    public LAppMinimumView getView() {
        return view;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public Activity getActivity() {
        return activity;
    }


    private LAppMinimumDelegate() {
        // Set up Cubism SDK framework.
        cubismOption.logFunction = new LAppMinimumPal.PrintLogFunction();
        cubismOption.loggingLevel = LAppDefine.cubismLoggingLevel;

        CubismFramework.cleanUp();
        CubismFramework.startUp(cubismOption);
    }

    private static LAppMinimumDelegate s_instance;
    private Activity activity;

    private final CubismFramework.Option cubismOption = new CubismFramework.Option();

    private LAppMinimumTextureManager textureManager;
    private LAppMinimumView view;
    private int windowWidth;
    private int windowHeight;

    /**
     * クリックしているか
     */
    private boolean isCaptured;
    /**
     * マウスのX座標
     */
    private float mouseX;
    /**
     * マウスのY座標
     */
    private float mouseY;

    private final float[] backgroundColor = {1f, 1f, 1f, 1f};
    private int backgroundTextureId = -1;
    private LAppMinimumSprite backgroundSprite;
    private int backgroundImageWidth;
    private int backgroundImageHeight;
}
