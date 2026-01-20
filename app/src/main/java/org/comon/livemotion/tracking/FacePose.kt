package org.comon.livemotion.tracking

/**
 * 전면 카메라 인식을 통해 계산된 얼굴의 자세 및 표정 상태 데이터
 *
 * @property yaw 좌우 회전 (-1.0 ~ 1.0, 정규화됨. Live2D 변환 시 -30~30으로 스케일)
 * @property pitch 상하 회전 (-1.0 ~ 1.0, 정규화됨)
 * @property roll 기울기 (-1.0 ~ 1.0, 정규화됨)
 * @property mouthOpen 입 벌림 정도 (0 ~ 1)
 * @property mouthForm 입 모양 (0 ~ 1, 0=보통, 1=미소)
 * @property eyeLOpen 왼쪽 눈 뜸 정도 (0 ~ 1)
 * @property eyeROpen 오른쪽 눈 뜸 정도 (0 ~ 1)
 * @property eyeBallX 눈동자 좌우 위치 (-1 ~ 1, 왼쪽 음수, 오른쪽 양수)
 * @property eyeBallY 눈동자 상하 위치 (-1 ~ 1, 위 양수, 아래 음수)
 */
data class FacePose(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val mouthOpen: Float = 0f,
    val mouthForm: Float = 0f,
    val eyeLOpen: Float = 1f,
    val eyeROpen: Float = 1f,
    val eyeBallX: Float = 0f,
    val eyeBallY: Float = 0f
) {
    override fun toString(): String {
        return String.format(
            "Yaw: %.2f, Pitch: %.2f, Roll: %.2f, Mouth: %.2f, MouthForm: %.2f, EyeL: %.2f, EyeR: %.2f, EyeBallX: %.2f, EyeBallY: %.2f",
            yaw, pitch, roll, mouthOpen, mouthForm, eyeLOpen, eyeROpen, eyeBallX, eyeBallY
        )
    }
}
