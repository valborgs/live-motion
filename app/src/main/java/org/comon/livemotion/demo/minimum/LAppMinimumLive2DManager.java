/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package org.comon.livemotion.demo.minimum;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
 */
public class LAppMinimumLive2DManager {
    public static LAppMinimumLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppMinimumLive2DManager();
        }
        return s_instance;
    }

    public static void releaseInstance() {
        s_instance = null;
    }

    public void loadModel(String modelDirectoryName) {
        if (model != null) {
            model.deleteModel();
            model = null;
        }
        String dir = modelDirectoryName + "/";
        model = new LAppMinimumModel(dir);
        model.loadAssets(dir, modelDirectoryName + ".model3.json");
    }

    // 캐릭터 스케일 및 오프셋 (확대/이동 기능)
    private float modelScale = 1.0f;
    private float modelOffsetX = 0.0f;
    private float modelOffsetY = 0.0f;

    /**
     * 캐릭터 스케일 설정
     * @param scale 1.0 = 기본 크기, 2.0 = 2배 확대
     */
    public void setModelScale(float scale) {
        this.modelScale = Math.max(0.5f, Math.min(10.0f, scale));
    }

    public float getModelScale() {
        return modelScale;
    }

    /**
     * 캐릭터 오프셋 설정
     * @param offsetX X축 이동 (-1.0 ~ 1.0)
     * @param offsetY Y축 이동 (-1.0 ~ 1.0)
     */
    public void setModelOffset(float offsetX, float offsetY) {
        this.modelOffsetX = Math.max(-10.0f, Math.min(10.0f, offsetX));
        this.modelOffsetY = Math.max(-10.0f, Math.min(10.0f, offsetY));
    }

    public float getModelOffsetX() {
        return modelOffsetX;
    }

    public float getModelOffsetY() {
        return modelOffsetY;
    }

    /**
     * 스케일 및 오프셋 리셋
     */
    public void resetModelTransform() {
        modelScale = 1.0f;
        modelOffsetX = 0.0f;
        modelOffsetY = 0.0f;
    }

    // モデル更新処理及び描画処理を行う
    public void onUpdate() {
        if (model == null || model.getModel() == null) {
            return;
        }

        int width = LAppMinimumDelegate.getInstance().getWindowWidth();
        int height = LAppMinimumDelegate.getInstance().getWindowHeight();

        projection.loadIdentity();

        if (model.getModel().getCanvasWidth() > 1.0f && width < height) {
            // 横に長いモデルを縦長ウィンドウに表示する際モデルの横サイズでscaleを算出する
            model.getModelMatrix().setWidth(2.0f);
            projection.scale(1.0f, (float) width / (float) height);
        } else {
            projection.scale((float) height / (float) width, 1.0f);
        }

        // 사용자 스케일 적용 (비율 유지)
        projection.scaleRelative(modelScale, modelScale);
        
        // 사용자 오프셋 적용
        projection.translateRelative(modelOffsetX / modelScale, modelOffsetY / modelScale);

        // 必要があればここで乗算する
        if (viewMatrix != null) {
            viewMatrix.multiplyByMatrix(projection);
        }

        // 描画前コール
        LAppMinimumDelegate.getInstance().getView().preModelDraw(model);

        model.update();
        model.draw(projection);     // 参照渡しなのでprojectionは変質する

        // 描画後コール
        LAppMinimumDelegate.getInstance().getView().postModelDraw(model);
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onDrag(float x, float y) {
        model.setDragging(x, y);
    }

    /**
     * 외부(얼굴 인식 등)로부터 전달받은 파라미터를 모델에 적용한다.
     * @param params 파라미터 ID와 값의 맵
     */
    public void applyFacePose(java.util.Map<String, Float> params) {
        if (model != null) {
            model.applyFacePose(params);
        }
    }

    /**
     * 現在のシーンで保持しているモデルを返す
     *
     * @param number モデルリストのインデックス値
     * @return モデルのインスタンスを返す。インデックス値が範囲外の場合はnullを返す
     */
    public LAppMinimumModel getModel(int number) {
        return model;
    }

    /**
     * シングルトンインスタンス
     */
    private static LAppMinimumLive2DManager s_instance;

    private LAppMinimumLive2DManager() {
        loadModel("gyana3");
    }

    private LAppMinimumModel model;

    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();
}

