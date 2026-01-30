/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */
package org.comon.live2d;

import android.util.Log;
import com.live2d.sdk.cubism.core.ICubismLogger;

import org.comon.live2d.LAppDefine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LAppMinimumPal {
    // 외부 모델 로딩을 위한 기본 경로
    private static String externalBasePath = null;

    /**
     * 외부 모델 로딩을 위한 기본 경로를 설정합니다.
     * 설정되면 파일 로딩 시 assets 대신 파일 시스템을 사용합니다.
     * @param path 모델 디렉토리의 절대 경로, 또는 null (assets 사용)
     */
    public static void setExternalBasePath(String path) {
        externalBasePath = path;
    }

    /**
     * 외부 기본 경로를 초기화하여 assets 로딩으로 되돌립니다.
     */
    public static void clearExternalBasePath() {
        externalBasePath = null;
    }

    /**
     * 현재 외부 경로에서 로딩 중인지 확인합니다.
     */
    public static boolean isExternalLoading() {
        return externalBasePath != null;
    }

    /**
     * 현재 설정된 외부 기본 경로를 반환합니다.
     */
    public static String getExternalBasePath() {
        return externalBasePath;
    }
    /**
     * Logging Function class to be registered in the CubismFramework's logging function.
     */
    public static class PrintLogFunction implements ICubismLogger {
        @Override
        public void print(String message) {
            Log.d(TAG, message);
        }
    }

    // アプリケーションを中断状態にする。実行されるとonPause()イベントが発生する
    public static void moveTaskToBack() {
        LAppMinimumDelegate.getInstance().getActivity().moveTaskToBack(true);
    }

    // デルタタイムの更新
    public static void updateTime() {
        s_currentFrame = getSystemNanoTime();
        deltaNanoTime = s_currentFrame - lastNanoTime;
        lastNanoTime = s_currentFrame;
    }

    // ファイルをバイト列として読み込む (assets 또는 파일 시스템에서)
    public static byte[] loadFileAsBytes(final String path) {
        InputStream fileData = null;
        try {
            if (externalBasePath != null) {
                // 파일 시스템에서 로드
                File file = new File(externalBasePath, path);
                if (!file.exists()) {
                    // 절대 경로로 시도
                    file = new File(path);
                }
                if (!file.exists()) {
                    if (LAppDefine.DEBUG_LOG_ENABLE) {
                        printLog("File not found: " + path);
                    }
                    return new byte[0];
                }
                fileData = new FileInputStream(file);
            } else {
                // assets에서 로드
                fileData = LAppMinimumDelegate.getInstance().getActivity().getAssets().open(path);
            }

            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);

            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File open error: " + path);
            }

            return new byte[0];
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File close error.");
                }
            }
        }
    }

    // デルタタイム(前回フレームとの差分)を取得する
    public static float getDeltaTime() {
        // ナノ秒を秒に変換
        return (float) (deltaNanoTime / 1000000000.0f);
    }

    /**
     * Logging function
     *
     * @param message log message
     */
    public static void printLog(String message) {
        Log.d(TAG, message);
    }

    private static long getSystemNanoTime() {
        return System.nanoTime();
    }

    private LAppMinimumPal() {}

    private static double s_currentFrame;
    private static double lastNanoTime;
    private static double deltaNanoTime;

    private static final String TAG = "[APP]";
}
