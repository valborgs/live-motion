/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package org.comon.live2d;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// テクスチャの管理を行うクラス
public class LAppMinimumTextureManager {
    // 画像情報データクラス
    public static class TextureInfo {
        public int id;  // テクスチャID
        public int width;   // 横幅
        public int height;  // 高さ
        public String filePath; // ファイル名
    }

    // 画像読み込み (assets 또는 파일 시스템에서)
    // imageFileOffset: glGenTexturesで作成したテクスチャの保存場所
    public TextureInfo createTextureFromPngFile(String filePath) {
        // search loaded texture already
        for (TextureInfo textureInfo : textures) {
            if (textureInfo.filePath.equals(filePath)) {
                return textureInfo;
            }
        }

        // 외부 경로 또는 assets에서 이미지 로드
        InputStream stream = null;
        try {
            if (LAppMinimumPal.isExternalLoading()) {
                // 파일 시스템에서 로드
                String basePath = LAppMinimumPal.getExternalBasePath();
                File file = new File(basePath, filePath);
                if (!file.exists()) {
                    file = new File(filePath);
                }
                stream = new FileInputStream(file);
            } else {
                // assets에서 로드
                AssetManager assetManager = LAppMinimumDelegate.getInstance().getActivity().getAssets();
                stream = assetManager.open(filePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        // decodeStreamは乗算済みアルファとして画像を読み込むようである
        Bitmap bitmap = BitmapFactory.decodeStream(stream);
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Texture0をアクティブにする
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // OpenGLにテクスチャを生成
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);

        // メモリ上の2D画像をテクスチャに割り当てる
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // ミップマップを生成する
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        // 縮小時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        // 拡大時の補間設定
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        TextureInfo textureInfo = new TextureInfo();
        textureInfo.filePath = filePath;
        textureInfo.width = bitmap.getWidth();
        textureInfo.height = bitmap.getHeight();
        textureInfo.id = textureId[0];
        textures.add(textureInfo);

        // bitmap解放
        bitmap.recycle();

        return textureInfo;
    }

    private final List<TextureInfo> textures = new ArrayList<TextureInfo>();        // 画像情報のリスト
}
