package org.comon.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.comon.domain.repository.IConsentRepository
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val consentRepository: IConsentRepository
) : ViewModel() {

    sealed class IntroEffect {
        data object NavigateToTitle : IntroEffect()
        data object NavigateToTermsOfService : IntroEffect()
    }

    private val _effect = Channel<IntroEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            val consentDeferred = async {
                try {
                    consentRepository.getLocalConsent()
                } catch (_: Exception) {
                    null
                }
            }
            delay(1000L)
            val consent = consentDeferred.await()
            if (consent != null) {
                _effect.send(IntroEffect.NavigateToTitle)
            } else {
                _effect.send(IntroEffect.NavigateToTermsOfService)
            }
        }
    }
}
