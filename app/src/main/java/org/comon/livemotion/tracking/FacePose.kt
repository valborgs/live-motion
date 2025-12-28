package org.comon.livemotion.tracking

/**
 * 전면 카메라 인식을 통해 계산된 얼굴의 자세 및 표정 상태 데이터
 */
data class FacePose(
    val yaw: Float = 0f,        // 좌우 회전 (-30 ~ 30)
    val pitch: Float = 0f,      // 상하 회전 (-30 ~ 30)
    val roll: Float = 0f,       // 기울기 (-30 ~ 30)
    val mouthOpen: Float = 0f,  // 입 벌림 정도 (0 ~ 1)
    val eyeLOpen: Float = 1f,   // 왼쪽 눈 뜸 (0 ~ 1)
    val eyeROpen: Float = 1f    // 오른쪽 눈 뜸 (0 ~ 1)
) {
    override fun toString(): String {
        return String.format(
            "Yaw: %.2f, Pitch: %.2f, Roll: %.2f, Mouth: %.2f, EyeL: %.2f, EyeR: %.2f",
            yaw, pitch, roll, mouthOpen, eyeLOpen, eyeROpen
        )
    }
}
