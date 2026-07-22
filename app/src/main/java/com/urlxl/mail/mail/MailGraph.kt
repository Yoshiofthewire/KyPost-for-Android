package com.urlxl.mail.mail

import android.content.Context
import com.urlxl.mail.SingletonGraph
import com.urlxl.mail.data.DataRuntime
import com.urlxl.mail.pairingHttpClient
import com.urlxl.mail.push.PairingData
import com.urlxl.mail.push.PushRuntime
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MailGraph(context: Context) {
    private val appContext = context.applicationContext
    private val mailCursorStore = MailCursorStore(appContext)
    private val pairingProvider = { PushRuntime.graph(appContext).repository.pairingForAuthenticatedCall() }
    private val relaySource: MailSource = RelayMailSource(
        pairingProvider = pairingProvider,
        cursorProvider = mailCursorStore,
        pinnedCallFactory = PinnedCallFactoryProvider(appContext, pairingProvider),
    )

    val repository = MailRepository(
        emailDao = DataRuntime.graph(appContext).database.emailDao(),
        relaySource = relaySource,
    )
}

/**
 * Builds (and caches) a TLS-pinned OkHttp client from the current pairing state, rebuilding only
 * when the pin or host actually change — e.g. on re-pairing after a legitimate cert rotation (see
 * [MailOutcome.CertificateMismatch]'s recovery path) — rather than reconnecting from scratch on
 * every relay call. Returns null (meaning: [RelayMailSource] falls back to its unpinned default
 * client) until a pin has actually been captured, i.e. before the very first successful pairing
 * completes — see [com.urlxl.mail.push.PushSyncCoordinator.attemptPairing].
 */
private class PinnedCallFactoryProvider(
    private val appContext: Context,
    private val pairingProvider: () -> PairingData?,
) : () -> Call.Factory? {
    @Volatile private var cachedKey: Pair<String, String>? = null
    @Volatile private var cachedClient: Call.Factory? = null

    override fun invoke(): Call.Factory? {
        val pin = PushRuntime.graph(appContext).repository.currentTlsPin() ?: return null
        val host = pairingProvider()?.serverUrl?.toHttpUrlOrNull()?.host ?: return null
        val key = pin to host
        cachedClient?.takeIf { cachedKey == key }?.let { return it }
        return pairingHttpClient(pinnedSpkiSha256 = pin, host = host).also {
            cachedClient = it
            cachedKey = key
        }
    }
}

object MailRuntime {
    private val holder = SingletonGraph(::MailGraph)

    fun graph(context: Context): MailGraph = holder.get(context)
}
