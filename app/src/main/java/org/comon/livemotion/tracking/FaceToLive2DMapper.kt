package org.comon.livemotion.tracking

import kotlin.math.absoluteValue

/**
 * 얼굴 트래킹 원시 데이터를 Live2D 파라미터로 매핑하고 스무딩(EMA)을 적용하는 클래스
 */
class FaceToLive2DMapper {
    private var lastPose = FacePose()
    private val alpha = 0.4f // 스무딩과 반응성의 균형 (0.3 -> 0.4)

    /**
     * 내부 상태를 초기화하여 얼굴이 나타날 때 이전 위치에서 튀는 현상을 방지
     */
    fun reset() {
        lastPose = FacePose()
    }

    /**
     * 새로운 얼굴 포즈를 받아 스무딩을 적용하고 Live2D 파라미터 맵으로 변환
     */
    fun map(newPose: FacePose): Map<String, Float> {
        // EMA 스무딩
        val smoothed = FacePose(
            yaw = smooth(lastPose.yaw, newPose.yaw),
            pitch = smooth(lastPose.pitch, newPose.pitch),
            roll = smooth(lastPose.roll, newPose.roll),
            mouthOpen = smooth(lastPose.mouthOpen, newPose.mouthOpen),
            eyeLOpen = smooth(lastPose.eyeLOpen, newPose.eyeLOpen),
            eyeROpen = smooth(lastPose.eyeROpen, newPose.eyeROpen)
        )
        lastPose = smoothed

        val params = mutableMapOf<String, Float>()
        
        // VTubeStudio 스타일 파라미터 매핑 (ID는 모델에 따라 다를 수 있으나 표준 ID 사용)
        // LAppMinimumModel에서 사용하는 ID에 맞춰 매핑
        // 드래그 로직의 가중치(30, 10, 1)와 결합하여 자연스러운 움직임 유도
        params["ParamAngleX"] = (smoothed.yaw * 30f).coerceIn(-30f, 30f)
        params["ParamAngleY"] = (smoothed.pitch * 40f).coerceIn(-30f, 30f) // 가중치 상향 (30 -> 40)
        
        // AngleZ는 실측 고개 기울기(roll)와 드래그 로직 특유의 수식(X*Y)을 혼합
        val dragStyleZ = smoothed.yaw * smoothed.pitch * (-30f)
        val realRollZ = smoothed.roll * 30f
        params["ParamAngleZ"] = (realRollZ + dragStyleZ).coerceIn(-30f, 30f)
        
        params["ParamEyeLOpen"] = smoothed.eyeLOpen.coerceIn(0f, 1f)
        params["ParamEyeROpen"] = smoothed.eyeROpen.coerceIn(0f, 1f)
        params["ParamMouthOpenY"] = smoothed.mouthOpen.coerceIn(0f, 1f)

        // 몸과 눈동자 연동 (드래그 로직 가중치 적용)
        params["ParamBodyAngleX"] = (smoothed.yaw * 10f).coerceIn(-10f, 10f)
        params["ParamEyeBallX"] = smoothed.yaw.coerceIn(-1f, 1f)
        
        // Pitch(상하 고개)가 시선에 미치는 영향을 낮춤 (0.5f 가중치)
        // 또한 Yaw 회전 시 발생하는 Pitch 오차를 일부 상쇄하기 위한 보정 시도
        val correctedPitch = smoothed.pitch - (smoothed.yaw.absoluteValue * 0.1f)
        params["ParamEyeBallY"] = (correctedPitch * 0.7f).coerceIn(-1f, 1f)

        // if (LAppDefine.DEBUG_LOG_ENABLE) {
        //     android.util.Log.d("Mapper", "Mapped Params: AngleX=${"%.2f".format(params["ParamAngleX"])}, AngleY=${"%.2f".format(params["ParamAngleY"])}")
        // }

        return params
    }

    private fun smooth(last: Float, current: Float): Float {
        return last + alpha * (current - last)
    }
}
