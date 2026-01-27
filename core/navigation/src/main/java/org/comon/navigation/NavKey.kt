package org.comon.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey {
    @Serializable
    data object ModelSelect : NavKey
    
    @Serializable
    data class Studio(val modelId: String) : NavKey
}
