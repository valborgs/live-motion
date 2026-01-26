package org.comon.livemotion

import android.content.Context
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
import org.comon.livemotion.demo.minimum.LAppMinimumDelegate
import org.comon.livemotion.demo.minimum.LAppMinimumLive2DManager

@Composable
fun Live2DScreen(
    modifier: Modifier = Modifier,
    modelId: String? = null,
    faceParams: Map<String, Float>? = null,
    isZoomEnabled: Boolean = false,
    isMoveEnabled: Boolean = false
) {
    val context: Context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val glView = remember {
        Live2DGLSurfaceView(context)
    }

    // 제스처 모드 업데이트
    LaunchedEffect(isZoomEnabled, isMoveEnabled) {
        glView.isZoomEnabled = isZoomEnabled
        glView.isMoveEnabled = isMoveEnabled
    }

    // 모델 ID 변경 시 GL Thread로 작업 큐잉
    LaunchedEffect(modelId) {
        modelId?.let { id ->
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().loadModel(id)
            }
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
            // 화면을 나갈 때 델리게이트와 매니저를 해제하여 텍스처 캐시 등을 초기화함
            LAppMinimumDelegate.getInstance().onStop()
            LAppMinimumDelegate.releaseInstance()
        }
    }
}

