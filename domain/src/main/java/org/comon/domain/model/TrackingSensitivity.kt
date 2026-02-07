package org.comon.domain.model

data class TrackingSensitivity(
    val yaw: Float = 1.0f,
    val pitch: Float = 1.0f,
    val roll: Float = 1.0f,
    val smoothing: Float = 0.4f
)
