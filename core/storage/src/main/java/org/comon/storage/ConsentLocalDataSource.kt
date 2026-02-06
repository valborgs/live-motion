package org.comon.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.comon.domain.model.UserConsent

private val Context.consentDataStore by preferencesDataStore(name = "user_consent")

class ConsentLocalDataSource(private val context: Context) {

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_TOS_VERSION = stringPreferencesKey("tos_version")
        private val KEY_AGREED_AT = longPreferencesKey("agreed_at")
        private val KEY_HAS_CONSENTED = booleanPreferencesKey("has_consented")
    }

    suspend fun getConsent(): UserConsent? {
        return context.consentDataStore.data.map { prefs ->
            val hasConsented = prefs[KEY_HAS_CONSENTED] ?: false
            if (!hasConsented) return@map null
            val userId = prefs[KEY_USER_ID] ?: return@map null
            val tosVersion = prefs[KEY_TOS_VERSION] ?: return@map null
            val agreedAt = prefs[KEY_AGREED_AT] ?: return@map null
            UserConsent(userId = userId, tosVersion = tosVersion, agreedAt = agreedAt)
        }.first()
    }

    suspend fun saveConsent(consent: UserConsent) {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = consent.userId
            prefs[KEY_TOS_VERSION] = consent.tosVersion
            prefs[KEY_AGREED_AT] = consent.agreedAt
            prefs[KEY_HAS_CONSENTED] = true
        }
    }
}
