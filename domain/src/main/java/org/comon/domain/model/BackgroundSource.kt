package org.comon.domain.model

data class ExternalBackground(
    val id: String,
    val name: String,
    val originalUri: String,
    val cachePath: String,
    val sizeBytes: Long,
    val cachedAt: Long,
)

sealed class BackgroundSource {
    data object Default : BackgroundSource()
    data class Asset(val name: String) : BackgroundSource()
    data class External(val background: ExternalBackground) : BackgroundSource()

    val id: String
        get() = when (this) {
            is Default -> "default_white"
            is Asset -> "asset_$name"
            is External -> background.id
        }

    val displayName: String
        get() = when (this) {
            is Default -> "White"
            is Asset -> name.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".jpeg")
            is External -> background.name
        }

    val isExternal: Boolean get() = this is External
}
