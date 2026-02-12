package org.comon.studio.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.comon.studio.recording.ModelRecorder
import org.comon.ui.theme.LiveMotionTheme

/**
 * 모델 뷰 오른쪽 하단에 표시되는 녹화 오버레이.
 *
 * 녹화 시간, 녹화/정지 토글 버튼, 일시정지/재개 토글 버튼을 포함합니다.
 * 이 오버레이는 Compose 레이어에 존재하므로 GL 녹화 영상에는 포함되지 않습니다.
 *
 * @param recordingState 현재 녹화 상태
 * @param elapsedMs 녹화 경과 시간 (밀리초)
 * @param onStartRecording 녹화 시작 콜백
 * @param onStopRecording 녹화 정지 콜백
 * @param onTogglePause 일시정지/재개 토글 콜백
 */
@Composable
internal fun RecordingOverlay(
    recordingState: ModelRecorder.RecordingState,
    elapsedMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = recordingState == ModelRecorder.RecordingState.RECORDING
    val isPaused = recordingState == ModelRecorder.RecordingState.PAUSED
    val isActive = isRecording || isPaused

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 녹화 시간 표시
        RecordingTimer(
            elapsedMs = elapsedMs,
            isRecording = isRecording,
            isPaused = isPaused,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 녹화 제어 버튼
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 녹화/정지 토글 버튼
            RecordStopButton(
                isActive = isActive,
                onClick = if (isActive) onStopRecording else onStartRecording,
            )

            // 일시정지/재개 토글 버튼 (녹화 중에만 활성)
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PauseResumeButton(
                    isPaused = isPaused,
                    onClick = onTogglePause,
                )
            }
        }
    }
}

@Composable
private fun RecordingTimer(
    elapsedMs: Long,
    isRecording: Boolean,
    isPaused: Boolean,
) {
    val totalSeconds = (elapsedMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeText = String.format("%02d:%02d", minutes, seconds)

    // 녹화 중 빨간 원 깜빡임
    val blinkAlpha = if (isRecording) {
        val transition = rememberInfiniteTransition(label = "recordBlink")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blinkAlpha",
        )
        alpha
    } else if (isPaused) {
        0.5f
    } else {
        1f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 빨간 녹화 인디케이터
        if (isRecording || isPaused) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(blinkAlpha)
                    .clip(CircleShape)
                    .background(Color.Red),
            )
        }

        Text(
            text = timeText,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RecordStopButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isActive) Color.White.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.8f),
        ),
    ) {
        if (isActive) {
            // 정지 아이콘 (네모)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        } else {
            // 녹화 아이콘 (빨간 원)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PauseResumeButton(
    isPaused: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.2f),
        ),
    ) {
        if (isPaused) {
            // 재생 아이콘 (삼각형을 근사적으로 표현)
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 14.sp,
            )
        } else {
            // 일시정지 아이콘 (두 줄)
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White),
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RecordingOverlayIdlePreview() {
    LiveMotionTheme {
        Box(modifier = Modifier.background(Color.DarkGray).padding(16.dp)) {
            RecordingOverlay(
                recordingState = ModelRecorder.RecordingState.IDLE,
                elapsedMs = 0,
                onStartRecording = {},
                onStopRecording = {},
                onTogglePause = {},
            )
        }
    }
}

@Preview
@Composable
private fun RecordingOverlayRecordingPreview() {
    LiveMotionTheme {
        Box(modifier = Modifier.background(Color.DarkGray).padding(16.dp)) {
            RecordingOverlay(
                recordingState = ModelRecorder.RecordingState.RECORDING,
                elapsedMs = 125_000,
                onStartRecording = {},
                onStopRecording = {},
                onTogglePause = {},
            )
        }
    }
}

@Preview
@Composable
private fun RecordingOverlayPausedPreview() {
    LiveMotionTheme {
        Box(modifier = Modifier.background(Color.DarkGray).padding(16.dp)) {
            RecordingOverlay(
                recordingState = ModelRecorder.RecordingState.PAUSED,
                elapsedMs = 65_000,
                onStartRecording = {},
                onStopRecording = {},
                onTogglePause = {},
            )
        }
    }
}
