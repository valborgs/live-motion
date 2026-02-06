package org.comon.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.UserConsent
import org.comon.domain.repository.IConsentRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TermsOfServiceViewModel @Inject constructor(
    private val consentRepository: IConsentRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val scrolledToBottom: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<TermsOfServiceUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onIntent(intent: TermsOfServiceUiIntent) {
        when (intent) {
            is TermsOfServiceUiIntent.Agree -> agree()
            is TermsOfServiceUiIntent.ScrolledToBottom -> {
                _uiState.update { it.copy(scrolledToBottom = true) }
            }
        }
    }

    private fun agree() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val consent = UserConsent(
                    userId = UUID.randomUUID().toString(),
                    tosVersion = TOS_VERSION,
                    agreedAt = System.currentTimeMillis()
                )
                consentRepository.saveConsent(consent)
            } catch (_: Exception) {
                // 저장 실패 시에도 진행
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                _uiEffect.send(TermsOfServiceUiEffect.NavigateToTitle)
            }
        }
    }

    companion object {
        const val TOS_VERSION = "1.0"
    }
}
