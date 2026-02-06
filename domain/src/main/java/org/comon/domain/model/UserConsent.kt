package org.comon.domain.model

data class UserConsent(
    val userId: String,
    val tosVersion: String,
    val agreedAt: Long
)
