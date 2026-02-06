package org.comon.home

sealed interface TermsOfServiceUiIntent {
    data object Agree : TermsOfServiceUiIntent
    data object ScrolledToBottom : TermsOfServiceUiIntent
}
