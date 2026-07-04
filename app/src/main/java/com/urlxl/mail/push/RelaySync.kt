package com.urlxl.mail.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

private const val RELAY_PLACEHOLDER_BASE_URL = "https://relay.invalid/"

@Serializable
data class RelayTokenSyncRequest(
    @SerialName("subscriberId") val subscriberId: String,
    @SerialName("pairingToken") val pairingToken: String,
    @SerialName("subscriberHash") val subscriberHash: String?,
    @SerialName("deviceToken") val deviceToken: String,
)

@Serializable
data class RelayTokenSyncResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("synced") val synced: Boolean = false,
)

object RelayRequestMapper {
    fun map(pairing: PairingData, token: String): RelayTokenSyncRequest {
        return RelayTokenSyncRequest(
            subscriberId = pairing.subscriberId,
            pairingToken = pairing.pairingToken,
            subscriberHash = pairing.subscriberHash.takeIf { it.isNotBlank() },
            deviceToken = token,
        )
    }
}

private interface RelayApi {
    @POST
    suspend fun syncToken(@Url url: String, @Body body: RelayTokenSyncRequest): Response<RelayTokenSyncResponse>
}

sealed class RelaySyncResult {
    data class Success(val syncedAtEpochMs: Long) : RelaySyncResult()
    data class Error(val message: String, val expiredPairingToken: Boolean = false) : RelaySyncResult()
}

class RelaySyncClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val api: RelayApi by lazy {
        Retrofit.Builder()
            .baseUrl(RELAY_PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RelayApi::class.java)
    }

    suspend fun syncToken(
        pairing: PairingData,
        token: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RelaySyncResult {
        if (token.isBlank()) return RelaySyncResult.Error("FCM token is empty")

        val request = RelayRequestMapper.map(pairing = pairing, token = token)

        var delayMs = 1_000L
        repeat(5) { attempt ->
            val result = runCatching { api.syncToken(pairing.relayUrl, request) }
            val response = result.getOrNull()

            if (response != null) {
                when (response.code()) {
                    200 -> {
                        val body = response.body()
                        return if (body?.ok == true && body.synced) {
                            RelaySyncResult.Success(syncedAtEpochMs = nowEpochMs)
                        } else {
                            RelaySyncResult.Error("Relay did not confirm sync")
                        }
                    }
                    400 -> return RelaySyncResult.Error("Malformed request or missing fields")
                    401 -> return RelaySyncResult.Error(
                        message = "Pairing token expired or invalid",
                        expiredPairingToken = true,
                    )
                    502, 503 -> {
                        if (attempt == 4) {
                            return RelaySyncResult.Error("Relay or upstream Novu issue (${response.code()})")
                        }
                    }
                    else -> return RelaySyncResult.Error("Failed to sync token (${response.code()})")
                }
            } else if (attempt == 4) {
                return RelaySyncResult.Error(result.exceptionOrNull()?.message ?: "Failed to sync token")
            }

            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }

        return RelaySyncResult.Error("Failed to sync token")
    }
}
