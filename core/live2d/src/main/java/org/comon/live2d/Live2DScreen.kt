package org.comon.live2d

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import org.comon.domain.model.ModelSource

@Composable
fun Live2DScreen(
    modifier: Modifier = Modifier,
    modelSource: ModelSource? = null,
    faceParams: Map<String, Float>? = null,
    isGestureEnabled: Boolean = false,
    backgroundPath: String? = null,
    effectFlow: Flow<Live2DUiEffect>? = null,
    onModelLoaded: (() -> Unit)? = null,
    onModelLoadError: ((String) -> Unit)? = null,
    onSurfaceSizeAvailable: ((width: Int, height: Int) -> Unit)? = null,
) {
    val context: Context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val glView = remember {
        Live2DGLSurfaceView(context)
    }

    // 제스처 모드 업데이트 (드래그 이동 + 핀치 확대/축소)
    LaunchedEffect(isGestureEnabled) {
        glView.isZoomEnabled = isGestureEnabled
        glView.isMoveEnabled = isGestureEnabled
    }

    // Live2D UI Effect 처리
    LaunchedEffect(effectFlow) {
        effectFlow?.collect { effect ->
            when (effect) {
                // 녹화 Surface는 GL queueEvent 밖에서 처리 (내부에서 queueEvent 사용)
                is Live2DUiEffect.SetRecordingSurface -> {
                    glView.setRecordingSurface(effect.surface)
                }
                // 그 외 효과는 GL 스레드에서 처리
                else -> {
                    glView.queueEvent {
                        val manager = LAppMinimumLive2DManager.getInstance()
                        when (effect) {
                            is Live2DUiEffect.StartExpression -> manager.startExpression(effect.path)
                            is Live2DUiEffect.ClearExpression -> manager.clearExpression()
                            is Live2DUiEffect.StartMotion -> manager.startMotion(effect.path)
                            is Live2DUiEffect.ClearMotion -> manager.clearMotion()
                            is Live2DUiEffect.ResetTransform -> manager.resetModelTransform()
                            is Live2DUiEffect.SetRecordingSurface -> { /* handled above */ }
                        }
                    }
                }
            }
        }
    }

    // 모델 소스 변경 시 GL Thread로 작업 큐잉
    LaunchedEffect(modelSource) {
        modelSource?.let { source ->
            val mainHandler = Handler(Looper.getMainLooper())
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().setOnModelLoadListener(
                    object : LAppMinimumLive2DManager.OnModelLoadListener {
                        override fun onModelLoaded() {
                            // GL 스레드에서 호출되므로 메인 스레드로 전환
                            mainHandler.post { onModelLoaded?.invoke() }
                        }
                        override fun onModelLoadError(error: String) {
                            // GL 스레드에서 호출되므로 메인 스레드로 전환
                            mainHandler.post { onModelLoadError?.invoke(error) }
                        }
                    }
                )
                when (source) {
                    is ModelSource.Asset -> {
                        LAppMinimumLive2DManager.getInstance().loadModel(source.modelId)
                    }
                    is ModelSource.External -> {
                        LAppMinimumLive2DManager.getInstance().loadExternalModel(
                            source.model.cachePath,
                            source.model.modelJsonName
                        )
                    }
                }
            }
        }
    }

    // 배경 이미지 변경 시 GL Thread로 전달
    LaunchedEffect(backgroundPath) {
        glView.queueEvent {
            if (backgroundPath != null) {
                LAppMinimumDelegate.getInstance().setBackgroundImage(backgroundPath)
            } else {
                LAppMinimumDelegate.getInstance().clearBackgroundImage()
            }
        }
    }

    // Surface 크기 콜백 (모델 로딩 완료 후 크기가 확정되므로 약간 지연 후 전달)
    LaunchedEffect(glView) {
        // GL Surface가 생성된 후 크기를 전달하기 위해 프레임 렌더링 후 확인
        kotlinx.coroutines.delay(500)
        val w = glView.surfaceWidth
        val h = glView.surfaceHeight
        if (w > 0 && h > 0) {
            onSurfaceSizeAvailable?.invoke(w, h)
        }
    }

    // 얼굴 파라미터 업데이트가 있을 때 GL Thread로 전달
    LaunchedEffect(faceParams) {
        faceParams?.let { params ->
            // Log.d("Live2DScreen", "Applying Face Params: $params")
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().applyFacePose(params)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { glView }
    )

    // GLSurfaceView resume/pause를 Lifecycle에 연결
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Live2D 리소스 정리는 StudioViewModel.onCleared()에서 수행
            // DisposableEffect.onDispose에서 정리하면 predictive back 제스처 취소 시
            // 리소스가 파괴되어 모델이 사라지는 문제 발생
        }
    }
}
