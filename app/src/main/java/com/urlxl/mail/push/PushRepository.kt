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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.pushDataStore by preferencesDataStore(name = "push_state")

private val KEY_APP_ID = stringPreferencesKey("pair_app")
private val KEY_SUBSCRIBER_ID = stringPreferencesKey("pair_sub")
private val KEY_SUBSCRIBER_HASH = stringPreferencesKey("pair_hash")
private val KEY_API_BASE = stringPreferencesKey("pair_api")
private val KEY_PAIR_SERVER_URL = stringPreferencesKey("pair_srv")
private val KEY_RELAY_URL = stringPreferencesKey("pair_relay")
private val KEY_PAIRING_TOKEN = stringPreferencesKey("pair_pt")
private val KEY_PAIRED_AT = longPreferencesKey("pair_paired_at")
private val KEY_LAST_SYNC_AT = longPreferencesKey("sync_last_at")
private val KEY_SYNC_ERROR = stringPreferencesKey("sync_error")
private val KEY_HISTORY_JSON = stringPreferencesKey("history_json")
private val KEY_SERVER_URL_SETTING = stringPreferencesKey("server_url_setting")

private const val HISTORY_LIMIT = 30

class PushRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<PushState> = context.pushDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map(::toState)

    suspend fun savePairing(pairing: PairingData) {
        context.pushDataStore.edit { prefs ->
            prefs[KEY_APP_ID] = pairing.applicationIdentifier
            prefs[KEY_SUBSCRIBER_ID] = pairing.subscriberId
            prefs[KEY_SUBSCRIBER_HASH] = pairing.subscriberHash
            prefs[KEY_API_BASE] = pairing.apiBase
            prefs[KEY_RELAY_URL] = pairing.relayUrl
            prefs[KEY_PAIRING_TOKEN] = pairing.pairingToken
            if (pairing.serverUrl.isNullOrBlank()) {
                prefs.remove(KEY_PAIR_SERVER_URL)
            } else {
                prefs[KEY_PAIR_SERVER_URL] = pairing.serverUrl
                prefs[KEY_SERVER_URL_SETTING] = pairing.serverUrl
            }
            prefs[KEY_PAIRED_AT] = pairing.pairedAtEpochMs
            prefs.remove(KEY_SYNC_ERROR)
        }
    }

    suspend fun clearPairing() {
        context.pushDataStore.edit { prefs ->
            prefs.remove(KEY_APP_ID)
            prefs.remove(KEY_SUBSCRIBER_ID)
            prefs.remove(KEY_SUBSCRIBER_HASH)
            prefs.remove(KEY_API_BASE)
            prefs.remove(KEY_PAIR_SERVER_URL)
            prefs.remove(KEY_RELAY_URL)
            prefs.remove(KEY_PAIRING_TOKEN)
            prefs.remove(KEY_PAIRED_AT)
            prefs.remove(KEY_LAST_SYNC_AT)
            prefs.remove(KEY_SYNC_ERROR)
        }
    }

    suspend fun saveServerUrlSetting(url: String) {
        context.pushDataStore.edit { prefs ->
            if (url.isBlank()) prefs.remove(KEY_SERVER_URL_SETTING) else prefs[KEY_SERVER_URL_SETTING] = url.trim()
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

    private fun toState(prefs: Preferences): PushState {
        val appId = prefs[KEY_APP_ID].orEmpty()
        val subId = prefs[KEY_SUBSCRIBER_ID].orEmpty()
        val subHash = prefs[KEY_SUBSCRIBER_HASH].orEmpty()
        val apiBase = prefs[KEY_API_BASE].orEmpty()
        val relayUrl = prefs[KEY_RELAY_URL].orEmpty()
        val pairingToken = prefs[KEY_PAIRING_TOKEN].orEmpty()
        val pairedAt = prefs[KEY_PAIRED_AT]
        val pairing = if (
            appId.isNotBlank() && subId.isNotBlank() && subHash.isNotBlank() &&
            apiBase.isNotBlank() && relayUrl.isNotBlank() && pairingToken.isNotBlank() && pairedAt != null
        ) {
            PairingData(
                applicationIdentifier = appId,
                subscriberId = subId,
                subscriberHash = subHash,
                apiBase = apiBase,
                serverUrl = prefs[KEY_PAIR_SERVER_URL],
                relayUrl = relayUrl,
                pairingToken = pairingToken,
                pairedAtEpochMs = pairedAt,
            )
        } else {
            null
        }

        val history = decodeHistory(prefs[KEY_HISTORY_JSON])
        return PushState(
            pairing = pairing,
            lastTokenSyncAtEpochMs = prefs[KEY_LAST_SYNC_AT],
            syncError = prefs[KEY_SYNC_ERROR],
            history = history,
            latestPayload = history.firstOrNull(),
            serverUrlSetting = prefs[KEY_SERVER_URL_SETTING],
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
    val serverUrlSetting: String? = null,
)

