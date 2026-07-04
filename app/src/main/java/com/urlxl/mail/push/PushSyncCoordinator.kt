package com.urlxl.mail.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class PushSyncCoordinator(
    private val repository: PushRepository,
    private val relayClient: RelaySyncClient,
) {
    suspend fun attemptPairing(pairing: PairingData): RelaySyncResult {
        val token = fetchFcmTokenOrNull()
            ?: return RelaySyncResult.Error("Unable to fetch FCM token")

        val result = relayClient.syncToken(pairing = pairing, token = token)
        if (result is RelaySyncResult.Success) {
            repository.savePairing(pairing)
            repository.updateSyncState(lastSyncAtEpochMs = result.syncedAtEpochMs, syncError = null)
        }
        return result
    }

    suspend fun syncCurrentPairingToken(): RelaySyncResult {
        val state = repository.stateSnapshot()
        val pairing = state.pairing ?: return RelaySyncResult.Error("Device is not paired")

        val token = fetchFcmTokenOrNull()
            ?: run {
                repository.updateSyncState(lastSyncAtEpochMs = null, syncError = "Unable to fetch FCM token")
                return RelaySyncResult.Error("Unable to fetch FCM token")
            }

        return syncAndPersist(pairing = pairing, token = token)
    }

    suspend fun syncProvidedToken(token: String): RelaySyncResult {
        val state = repository.stateSnapshot()
        val pairing = state.pairing ?: return RelaySyncResult.Error("Device is not paired")
        return syncAndPersist(pairing = pairing, token = token)
    }

    private suspend fun syncAndPersist(pairing: PairingData, token: String): RelaySyncResult {
        val result = relayClient.syncToken(pairing = pairing, token = token)
        when (result) {
            is RelaySyncResult.Success -> repository.updateSyncState(lastSyncAtEpochMs = result.syncedAtEpochMs, syncError = null)
            is RelaySyncResult.Error -> repository.updateSyncState(lastSyncAtEpochMs = null, syncError = result.message)
        }
        return result
    }

    private suspend fun fetchFcmTokenOrNull(): String? {
        return runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
    }
}

private suspend fun PushRepository.stateSnapshot(): PushState {
    return state.first()
}
