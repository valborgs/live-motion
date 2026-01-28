package org.comon.tracking

import android.content.Context
import androidx.lifecycle.LifecycleOwner

/**
 * FaceTracker 생성을 담당하는 Factory 클래스
 * ViewModel이 Context를 직접 참조하지 않도록 합니다.
 */
class FaceTrackerFactory(private val context: Context) {
    
    /**
     * 새 FaceTracker 인스턴스를 생성합니다.
     * @param lifecycleOwner CameraX 바인딩에 사용될 LifecycleOwner
     */
    fun create(lifecycleOwner: LifecycleOwner): FaceTracker {
        return FaceTracker(context, lifecycleOwner)
    }
}
