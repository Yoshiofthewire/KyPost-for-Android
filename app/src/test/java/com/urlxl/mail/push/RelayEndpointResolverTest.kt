package com.urlxl.mail.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEndpointResolverTest {

    @Test
    fun resolve_relayPresent_winsOverSrvAndManual() {
        val result = RelayEndpointResolver.resolve(
            qrRelay = "https://relay.example.com/fcm",
            qrServerUrl = "https://server.example.com",
            manualServerUrl = "https://manual.example.com",
        )

        val resolved = result as RelayEndpointResolver.Resolution.Resolved
        assertEquals("https://relay.example.com/fcm", resolved.relayUrl)
        assertEquals("https://server.example.com", resolved.effectiveServerUrl)
    }

    @Test
    fun resolve_relayMissing_derivesFromSrv() {
        val result = RelayEndpointResolver.resolve(
            qrRelay = null,
            qrServerUrl = "https://server.example.com/",
            manualServerUrl = null,
        )

        val resolved = result as RelayEndpointResolver.Resolution.Resolved
        assertEquals("https://server.example.com/api/notifications/novu/relay/fcm", resolved.relayUrl)
        assertEquals("https://server.example.com", resolved.effectiveServerUrl)
    }

    @Test
    fun resolve_relayAndSrvMissing_derivesFromManualSetting() {
        val result = RelayEndpointResolver.resolve(
            qrRelay = null,
            qrServerUrl = null,
            manualServerUrl = "https://manual.example.com",
        )

        val resolved = result as RelayEndpointResolver.Resolution.Resolved
        assertEquals("https://manual.example.com/api/notifications/novu/relay/fcm", resolved.relayUrl)
        assertEquals("https://manual.example.com", resolved.effectiveServerUrl)
    }

    @Test
    fun resolve_nothingAvailable_blocksPairing() {
        val result = RelayEndpointResolver.resolve(qrRelay = null, qrServerUrl = null, manualServerUrl = null)

        assertTrue(result is RelayEndpointResolver.Resolution.MissingServerUrl)
    }
}
