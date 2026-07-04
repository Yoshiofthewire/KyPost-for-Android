package com.urlxl.mail.push

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.pushDataStore by preferencesDataStore(name = "push_state")

private val KEY_LAST_SYNC_AT = longPreferencesKey("sync_last_at")
private val KEY_SYNC_ERROR = stringPreferencesKey("sync_error")
private val KEY_HISTORY_JSON = stringPreferencesKey("history_json")

private const val HISTORY_LIMIT = 30

class PushRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val securePairingStore = SecurePairingStore(context)

    val state: Flow<PushState> = combine(
        context.pushDataStore.data.catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        },
        securePairingStore.pairing,
    ) { prefs, pairing -> toState(prefs, pairing) }

    suspend fun savePairing(pairing: PairingData) {
        securePairingStore.savePairing(pairing)
        context.pushDataStore.edit { prefs ->
            prefs.remove(KEY_SYNC_ERROR)
        }
    }

    suspend fun clearPairing() {
        securePairingStore.clearPairing()
        context.pushDataStore.edit { prefs ->
            prefs.remove(KEY_LAST_SYNC_AT)
            prefs.remove(KEY_SYNC_ERROR)
        }
    }

    suspend fun updateSyncState(lastSyncAtEpochMs: Long?, syncError: String?) {
        context.pushDataStore.edit { prefs ->
            if (lastSyncAtEpochMs == null) prefs.remove(KEY_LAST_SYNC_AT) else prefs[KEY_LAST_SYNC_AT] = lastSyncAtEpochMs
            if (syncError.isNullOrBlank()) prefs.remove(KEY_SYNC_ERROR) else prefs[KEY_SYNC_ERROR] = syncError
        }
    }

    suspend fun appendPayload(payload: PushPayload) {
        context.pushDataStore.edit { prefs ->
            val current = decodeHistory(prefs[KEY_HISTORY_JSON])
            val updated = (listOf(payload) + current)
                .distinctBy { it.messageId }
                .take(HISTORY_LIMIT)
            prefs[KEY_HISTORY_JSON] = json.encodeToString(updated)
        }
    }

    private fun toState(prefs: Preferences, pairing: PairingData?): PushState {
        val history = decodeHistory(prefs[KEY_HISTORY_JSON])
        return PushState(
            pairing = pairing,
            lastTokenSyncAtEpochMs = prefs[KEY_LAST_SYNC_AT],
            syncError = prefs[KEY_SYNC_ERROR],
            history = history,
            latestPayload = history.firstOrNull(),
        )
    }

    private fun decodeHistory(value: String?): List<PushPayload> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PushPayload>>(value) }.getOrDefault(emptyList())
    }
}

data class PushState(
    val pairing: PairingData?,
    val lastTokenSyncAtEpochMs: Long?,
    val syncError: String?,
    val latestPayload: PushPayload?,
    val history: List<PushPayload>,
)
