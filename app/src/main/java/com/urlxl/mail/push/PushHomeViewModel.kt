package com.urlxl.mail.push

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PushHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = PushRuntime.graph(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val isWorking = MutableStateFlow(false)
    private val localMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PushHomeUiState> = combine(
        graph.repository.state,
        isWorking,
        localMessage,
    ) { repo, working, local ->
        PushHomeUiState(
            pairing = repo.pairing,
            lastTokenSyncAtEpochMs = repo.lastTokenSyncAtEpochMs,
            syncError = repo.syncError,
            latestPayload = repo.latestPayload,
            history = repo.history,
            serverUrlSetting = repo.serverUrlSetting,
            isWorking = working,
            localMessage = local,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PushHomeUiState(),
    )

    init {
        scope.launch {
            val state = graph.repository.state.first()
            if (state.pairing != null) {
                graph.syncCoordinator.syncCurrentPairingToken()
            }
        }
    }

    fun consumeLocalMessage() {
        localMessage.value = null
    }

    fun pairFromLink(link: String) {
        scope.launch {
            isWorking.value = true
            val parsed = NovuPairingDeepLinkParser.parse(link)
            when (parsed) {
                is PairingParseResult.Error -> {
                    localMessage.value = parsed.reason
                    isWorking.value = false
                }
                is PairingParseResult.Success -> {
                    val manualServerUrl = graph.repository.state.first().serverUrlSetting
                    val resolution = RelayEndpointResolver.resolve(
                        qrRelay = parsed.pairing.relayUrl.takeIf { it.isNotBlank() },
                        qrServerUrl = parsed.pairing.serverUrl,
                        manualServerUrl = manualServerUrl,
                    )
                    when (resolution) {
                        is RelayEndpointResolver.Resolution.MissingServerUrl -> {
                            localMessage.value = "Set a Server URL before pairing"
                            isWorking.value = false
                        }
                        is RelayEndpointResolver.Resolution.Resolved -> {
                            val pending = parsed.pairing.copy(
                                relayUrl = resolution.relayUrl,
                                serverUrl = resolution.effectiveServerUrl,
                            )
                            val result = graph.syncCoordinator.attemptPairing(pending)
                            localMessage.value = when (result) {
                                is RelaySyncResult.Success -> "Device paired and token synced"
                                is RelaySyncResult.Error -> {
                                    val suffix = if (result.expiredPairingToken) " — rescan the pairing QR code" else ""
                                    "Pairing failed: ${result.message}$suffix"
                                }
                            }
                            isWorking.value = false
                        }
                    }
                }
            }
        }
    }

    fun clearPairing() {
        scope.launch {
            isWorking.value = true
            graph.repository.clearPairing()
            localMessage.value = "Local pairing cleared"
            isWorking.value = false
        }
    }

    fun resyncToken() {
        scope.launch {
            isWorking.value = true
            val result = graph.syncCoordinator.syncCurrentPairingToken()
            localMessage.value = when (result) {
                is RelaySyncResult.Success -> "Token synced"
                is RelaySyncResult.Error -> {
                    val suffix = if (result.expiredPairingToken) " — rescan the pairing QR code" else ""
                    "Token sync failed: ${result.message}$suffix"
                }
            }
            isWorking.value = false
        }
    }

    fun setServerUrl(url: String) {
        scope.launch {
            graph.repository.saveServerUrlSetting(url)
            localMessage.value = "Server URL saved"
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

data class PushHomeUiState(
    val pairing: PairingData? = null,
    val lastTokenSyncAtEpochMs: Long? = null,
    val syncError: String? = null,
    val latestPayload: PushPayload? = null,
    val history: List<PushPayload> = emptyList(),
    val serverUrlSetting: String? = null,
    val isWorking: Boolean = false,
    val localMessage: String? = null,
)

