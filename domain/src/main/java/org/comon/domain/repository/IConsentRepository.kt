package org.comon.domain.repository

import org.comon.domain.model.UserConsent

interface IConsentRepository {
    suspend fun getLocalConsent(): UserConsent?
    suspend fun saveConsent(consent: UserConsent)
}
