package com.urlxl.mail.push

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

const val DEFAULT_NOVU_API_BASE = "https://api.novu.co"

@Serializable
data class PairingData(
    val applicationIdentifier: String,
    val subscriberId: String,
    val subscriberHash: String,
    val apiBase: String,
    val serverUrl: String?,
    val relayUrl: String,
    val pairingToken: String,
    val pairedAtEpochMs: Long,
)

object RelayEndpointResolver {
    sealed class Resolution {
        data class Resolved(val relayUrl: String, val effectiveServerUrl: String?) : Resolution()
        object MissingServerUrl : Resolution()
    }

    fun resolve(qrRelay: String?, qrServerUrl: String?, manualServerUrl: String?): Resolution {
        val relay = qrRelay?.takeIf { it.isNotBlank() }
        if (relay != null) {
            val effectiveServerUrl = qrServerUrl?.takeIf { it.isNotBlank() }
                ?: manualServerUrl?.takeIf { it.isNotBlank() }
            return Resolution.Resolved(relayUrl = relay, effectiveServerUrl = effectiveServerUrl)
        }

        val srv = (qrServerUrl?.takeIf { it.isNotBlank() } ?: manualServerUrl?.takeIf { it.isNotBlank() })
            ?.trimEnd('/')
            ?: return Resolution.MissingServerUrl

        return Resolution.Resolved(
            relayUrl = "$srv/api/notifications/novu/relay/fcm",
            effectiveServerUrl = srv,
        )
    }
}

data class PairingValidationResult(
    val isValid: Boolean,
    val message: String? = null,
)

sealed class PairingParseResult {
    data class Success(val pairing: PairingData) : PairingParseResult()
    data class Error(val reason: String) : PairingParseResult()
}

object PairingValidator {
    fun validate(app: String, sub: String, hash: String): PairingValidationResult {
        if (app.isBlank()) return PairingValidationResult(false, "Missing app parameter")
        if (sub.isBlank()) return PairingValidationResult(false, "Missing sub parameter")
        if (hash.isBlank()) return PairingValidationResult(false, "Missing hash parameter")
        return PairingValidationResult(true)
    }
}

object NovuPairingDeepLinkParser {
    fun parse(link: String, nowEpochMs: Long = System.currentTimeMillis()): PairingParseResult {
        val uri = runCatching { URI(link.trim()) }.getOrNull()
            ?: return PairingParseResult.Error("Invalid deep link")

        if (!uri.scheme.equals("llamalabels", ignoreCase = true) ||
            !uri.host.equals("novu-pair", ignoreCase = true)
        ) {
            return PairingParseResult.Error("Unsupported deep link")
        }

        val query = parseQuery(uri.rawQuery.orEmpty())

        val app = query["app"].orEmpty().trim()
        val sub = query["sub"].orEmpty().trim()
        val hash = query["hash"].orEmpty().trim()
        val apiRaw = query["api"].orEmpty().trim()
        val apiBase = normalizeApiBase(apiRaw.ifBlank { DEFAULT_NOVU_API_BASE })
            ?: return PairingParseResult.Error("Invalid api base")
        val srv = query["srv"].orEmpty().trim().takeIf { it.isNotBlank() }
        val relay = query["relay"].orEmpty().trim().takeIf { it.isNotBlank() }
        val pt = query["pt"].orEmpty().trim()

        val validation = PairingValidator.validate(app = app, sub = sub, hash = hash)
        if (!validation.isValid) {
            return PairingParseResult.Error(validation.message ?: "Invalid pairing parameters")
        }
        if (pt.isBlank()) {
            return PairingParseResult.Error("Missing pairing token")
        }

        return PairingParseResult.Success(
            PairingData(
                applicationIdentifier = app,
                subscriberId = sub,
                subscriberHash = hash,
                apiBase = apiBase,
                serverUrl = srv,
                relayUrl = relay.orEmpty(),
                pairingToken = pt,
                pairedAtEpochMs = nowEpochMs,
            ),
        )
    }

    private fun normalizeApiBase(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        val path = uri.path.orEmpty().trimEnd('/').takeIf { it.isNotBlank() } ?: ""
        return "$scheme://$host$path"
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index < 0) return@mapNotNull null
                val key = decode(part.substring(0, index))
                val value = decode(part.substring(index + 1))
                key to value
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

