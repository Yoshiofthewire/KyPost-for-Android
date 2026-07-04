package com.urlxl.mail.push

import android.os.Build
import com.urlxl.mail.BuildConfig
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

private const val REGISTRATION_PLACEHOLDER_BASE_URL = "https://native-register.invalid/"

@Serializable
data class NativeRegistrationRequest(
    @SerialName("subscriberId") val subscriberId: String,
    @SerialName("subscriberHash") val subscriberHash: String?,
    @SerialName("pairingToken") val pairingToken: String,
    @SerialName("deviceToken") val deviceToken: String,
    @SerialName("deviceId") val deviceId: String?,
    @SerialName("platform") val platform: String,
    @SerialName("deviceName") val deviceName: String?,
    @SerialName("appVersion") val appVersion: String?,
)

@Serializable
data class NativeRegistrationResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("synced") val synced: Boolean = false,
    @SerialName("deviceId") val deviceId: String? = null,
)

object NativeRegistrationRequestMapper {
    fun map(pairing: PairingData, token: String): NativeRegistrationRequest {
        return NativeRegistrationRequest(
            subscriberId = pairing.subscriberId,
            subscriberHash = pairing.subscriberHash.takeIf { it.isNotBlank() },
            pairingToken = pairing.pairingToken,
            deviceToken = token,
            deviceId = pairing.deviceId,
            platform = "android",
            deviceName = Build.MODEL,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
}

private interface NativeRegistrationApi {
    @POST
    suspend fun register(@Url url: String, @Body body: NativeRegistrationRequest): Response<NativeRegistrationResponse>
}

sealed class NativeRegistrationResult {
    data class Success(val syncedAtEpochMs: Long, val deviceId: String?) : NativeRegistrationResult()
    data class Error(val message: String, val expiredPairingToken: Boolean = false) : NativeRegistrationResult()
}

class NativeRegistrationClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val api: NativeRegistrationApi by lazy {
        Retrofit.Builder()
            .baseUrl(REGISTRATION_PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NativeRegistrationApi::class.java)
    }

    suspend fun register(
        pairing: PairingData,
        token: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): NativeRegistrationResult {
        if (token.isBlank()) return NativeRegistrationResult.Error("FCM token is empty")

        val request = NativeRegistrationRequestMapper.map(pairing = pairing, token = token)

        val result = runCatching { api.register(pairing.registrationUrl, request) }
        val response = result.getOrNull()
            ?: return NativeRegistrationResult.Error(result.exceptionOrNull()?.message ?: "Failed to register device")

        return when (response.code()) {
            200 -> {
                val body = response.body()
                if (body?.ok == true && body.synced) {
                    NativeRegistrationResult.Success(syncedAtEpochMs = nowEpochMs, deviceId = body.deviceId)
                } else {
                    NativeRegistrationResult.Error("Registration did not confirm sync")
                }
            }
            400 -> NativeRegistrationResult.Error("Malformed request or missing fields")
            401 -> NativeRegistrationResult.Error(
                message = "Pairing token expired or invalid",
                expiredPairingToken = true,
            )
            503 -> NativeRegistrationResult.Error("Pairing not configured on backend")
            else -> NativeRegistrationResult.Error("Failed to register device (${response.code()})")
        }
    }
}
