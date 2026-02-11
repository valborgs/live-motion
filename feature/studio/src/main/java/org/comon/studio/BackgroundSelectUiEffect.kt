package org.comon.studio

sealed class BackgroundSelectUiEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null
    ) : BackgroundSelectUiEffect()

    data class ShowErrorWithDetail(
        val displayMessage: String,
        val detailMessage: String
    ) : BackgroundSelectUiEffect()
}
