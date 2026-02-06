package org.comon.home

sealed class TermsOfServiceUiEffect {
    data object NavigateToTitle : TermsOfServiceUiEffect()
}
