package org.comon.studio.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Live2D 모델 뷰 영상 녹화를 위한 [MediaRecorder] 래퍼.
 *
 * [MediaRecorder.VideoSource.SURFACE]를 사용하여 GL 렌더러에서 직접
 * 프레임을 수신합니다. 선택적으로 마이크 음성도 함께 녹음합니다.
 *
 * ## 사용 흐름
 * 1. [prepare] → [inputSurface] 획득 → GL 렌더러에 Surface 전달
 * 2. [start] → 녹화 시작
 * 3. [pause] / [resume] → 일시정지/재개
 * 4. [stop] → 녹화 종료, 임시 파일 경로 반환
 * 5. [release] → 리소스 해제
 *
 * @see [RecordingState] 현재 녹화 상태
 */
class ModelRecorder {

    companion object {
        private const val TAG = "ModelRecorder"

        /** 최대 녹화 시간 (밀리초): 5분 */
        const val MAX_DURATION_MS = 300_000

        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE = 8_000_000
        private const val AUDIO_BIT_RATE = 128_000
        private const val AUDIO_SAMPLE_RATE = 44100
    }

    /** 녹화 상태 */
    enum class RecordingState {
        IDLE,
        PREPARED,
        RECORDING,
        PAUSED,
    }

    /** 현재 녹화 상태 */
    var state: RecordingState = RecordingState.IDLE
        private set

    /** MediaRecorder의 입력 Surface (prepare 후 사용 가능) */
    var inputSurface: Surface? = null
        private set

    /** 녹화된 임시 파일 */
    var tempFile: File? = null
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var onMaxDurationReached: (() -> Unit)? = null
    private var onRecordingError: ((String) -> Unit)? = null

    /**
     * MediaRecorder를 설정하고 녹화를 준비합니다.
     *
     * @param context 앱 컨텍스트
     * @param width 비디오 너비 (짝수로 정렬됨)
     * @param height 비디오 높이 (짝수로 정렬됨)
     * @param hasAudioPermission RECORD_AUDIO 권한 보유 여부
     * @param onMaxDuration 최대 녹화 시간 도달 콜백
     * @param onError 녹화 에러 콜백
     * @return 성공 시 입력 Surface, 실패 시 null
     */
    fun prepare(
        context: Context,
        width: Int,
        height: Int,
        hasAudioPermission: Boolean,
        onMaxDuration: () -> Unit,
        onError: (String) -> Unit,
    ): Surface? {
        if (state != RecordingState.IDLE) {
            Log.w(TAG, "prepare() called in invalid state: $state")
            return null
        }

        onMaxDurationReached = onMaxDuration
        onRecordingError = onError

        // 해상도를 짝수로 정렬 (인코더 호환성)
        val videoWidth = width and 0x7FFFFFFE  // 가장 가까운 짝수로 내림
        val videoHeight = height and 0x7FFFFFFE

        if (videoWidth <= 0 || videoHeight <= 0) {
            Log.e(TAG, "Invalid video dimensions: ${videoWidth}x${videoHeight}")
            onError("잘못된 비디오 해상도입니다")
            return null
        }

        return try {
            val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.mp4")
            tempFile = file

            val recorder = createMediaRecorder(context)

            recorder.apply {
                if (hasAudioPermission) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (hasAudioPermission) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }

                setVideoSize(videoWidth, videoHeight)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)

                if (hasAudioPermission) {
                    setAudioEncodingBitRate(AUDIO_BIT_RATE)
                    setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                }

                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(file.absolutePath)

                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d(TAG, "Max duration reached")
                        onMaxDurationReached?.invoke()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    val message = when (what) {
                        MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN -> "알 수 없는 녹화 오류 (code: $extra)"
                        MediaRecorder.MEDIA_ERROR_SERVER_DIED -> "미디어 서버 오류"
                        else -> "녹화 오류 발생 (what=$what, extra=$extra)"
                    }
                    onRecordingError?.invoke(message)
                }

                prepare()
            }

            mediaRecorder = recorder
            inputSurface = recorder.surface
            state = RecordingState.PREPARED

            Log.d(TAG, "Prepared: ${videoWidth}x${videoHeight}, audio=$hasAudioPermission")
            recorder.surface

        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaRecorder", e)
            cleanupAfterError()
            onError("녹화 준비 실패: ${e.localizedMessage}")
            null
        }
    }

    /**
     * 녹화를 시작합니다.
     */
    fun start(): Boolean {
        if (state != RecordingState.PREPARED) {
            Log.w(TAG, "start() called in invalid state: $state")
            return false
        }
        return try {
            mediaRecorder?.start()
            state = RecordingState.RECORDING
            Log.d(TAG, "Recording started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanupAfterError()
            onRecordingError?.invoke("녹화 시작 실패: ${e.localizedMessage}")
            false
        }
    }

    /**
     * 녹화를 일시정지합니다.
     */
    fun pause(): Boolean {
        if (state != RecordingState.RECORDING) {
            Log.w(TAG, "pause() called in invalid state: $state")
            return false
        }
        return try {
            mediaRecorder?.pause()
            state = RecordingState.PAUSED
            Log.d(TAG, "Recording paused")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recording", e)
            onRecordingError?.invoke("녹화 일시정지 실패: ${e.localizedMessage}")
            false
        }
    }

    /**
     * 일시정지된 녹화를 재개합니다.
     */
    fun resume(): Boolean {
        if (state != RecordingState.PAUSED) {
            Log.w(TAG, "resume() called in invalid state: $state")
            return false
        }
        return try {
            mediaRecorder?.resume()
            state = RecordingState.RECORDING
            Log.d(TAG, "Recording resumed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recording", e)
            onRecordingError?.invoke("녹화 재개 실패: ${e.localizedMessage}")
            false
        }
    }

    /**
     * 녹화를 정지하고 파일을 완성합니다.
     *
     * @return 녹화된 임시 파일. 실패 시 null.
     */
    fun stop(): File? {
        if (state != RecordingState.RECORDING && state != RecordingState.PAUSED) {
            Log.w(TAG, "stop() called in invalid state: $state")
            return null
        }
        return try {
            mediaRecorder?.stop()
            state = RecordingState.IDLE
            Log.d(TAG, "Recording stopped: ${tempFile?.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            state = RecordingState.IDLE
            // stop 실패 시 파일이 손상되었을 수 있음
            deleteTempFile()
            onRecordingError?.invoke("녹화 정지 실패: ${e.localizedMessage}")
            null
        }
    }

    /**
     * 모든 리소스를 해제합니다. 녹화 중이면 자동으로 정지합니다.
     */
    fun release() {
        try {
            if (state == RecordingState.RECORDING || state == RecordingState.PAUSED) {
                try {
                    mediaRecorder?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping during release", e)
                }
            }
            inputSurface?.release()
            inputSurface = null
            mediaRecorder?.release()
            mediaRecorder = null
            state = RecordingState.IDLE
            Log.d(TAG, "Released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during release", e)
        }
    }

    /**
     * 임시 파일을 삭제합니다.
     */
    fun deleteTempFile() {
        tempFile?.let { file ->
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Temp file deleted: $deleted (${file.absolutePath})")
            }
        }
        tempFile = null
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Private
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private fun cleanupAfterError() {
        try {
            inputSurface?.release()
            inputSurface = null
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        state = RecordingState.IDLE
        deleteTempFile()
    }
}
