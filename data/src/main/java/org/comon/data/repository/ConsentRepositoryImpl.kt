package org.comon.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import org.comon.domain.model.UserConsent
import org.comon.domain.repository.IConsentRepository
import org.comon.storage.ConsentLocalDataSource
import javax.inject.Inject

class ConsentRepositoryImpl @Inject constructor(
    private val localDataSource: ConsentLocalDataSource,
    private val firestore: FirebaseFirestore
) : IConsentRepository {

    override suspend fun getLocalConsent(): UserConsent? {
        return localDataSource.getConsent()
    }

    override suspend fun saveConsent(consent: UserConsent) {
        localDataSource.saveConsent(consent)

        try {
            val data = mapOf(
                "userId" to consent.userId,
                "tosVersion" to consent.tosVersion,
                "agreedAt" to Timestamp(Date(consent.agreedAt))
            )
            firestore.collection("consents")
                .document(consent.userId)
                .set(data)
                .await()
        } catch (_: Exception) {
            // Firestore 실패 시에도 로컬 저장은 완료되었으므로 진행
            // Firestore SDK 오프라인 캐시가 연결 복구 시 자동 동기화
        }
    }
}
