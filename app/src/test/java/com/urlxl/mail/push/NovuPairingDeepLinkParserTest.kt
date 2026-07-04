package com.urlxl.mail.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovuPairingDeepLinkParserTest {

    @Test
    fun parse_validDeepLink_extractsRequiredParams() {
        val result = NovuPairingDeepLinkParser.parse(
            "llamalabels://novu-pair?app=my-app&sub=subscriber-123&hash=secureHash&api=https%3A%2F%2Fapi.novu.co&pt=short-lived-token",
            nowEpochMs = 123L,
        )

        assertTrue(result is PairingParseResult.Success)
        val pairing = (result as PairingParseResult.Success).pairing
        assertEquals("my-app", pairing.applicationIdentifier)
        assertEquals("subscriber-123", pairing.subscriberId)
        assertEquals("secureHash", pairing.subscriberHash)
        assertEquals("https://api.novu.co", pairing.apiBase)
        assertEquals("short-lived-token", pairing.pairingToken)
        assertNull(pairing.serverUrl)
        assertEquals("", pairing.relayUrl)
        assertEquals(123L, pairing.pairedAtEpochMs)
    }

    @Test
    fun parse_missingApi_usesDefault() {
        val result = NovuPairingDeepLinkParser.parse(
            "llamalabels://novu-pair?app=my-app&sub=subscriber-123&hash=secureHash&pt=short-lived-token",
        )

        val pairing = (result as PairingParseResult.Success).pairing
        assertEquals(DEFAULT_NOVU_API_BASE, pairing.apiBase)
    }

    @Test
    fun parse_invalidHost_returnsError() {
        val result = NovuPairingDeepLinkParser.parse(
            "llamalabels://other-host?app=a&sub=b&hash=c&pt=d",
        )

        assertTrue(result is PairingParseResult.Error)
    }

    @Test
    fun parse_srvAndRelay_areExtracted() {
        val result = NovuPairingDeepLinkParser.parse(
            "llamalabels://novu-pair?app=my-app&sub=subscriber-123&hash=secureHash&pt=short-lived-token" +
                "&srv=https%3A%2F%2Fserver.example.com&relay=https%3A%2F%2Fserver.example.com%2Fapi%2Fnotifications%2Fnovu%2Frelay%2Ffcm",
        )

        assertTrue(result is PairingParseResult.Success)
        val pairing = (result as PairingParseResult.Success).pairing
        assertEquals("https://server.example.com", pairing.serverUrl)
        assertEquals("https://server.example.com/api/notifications/novu/relay/fcm", pairing.relayUrl)
    }

    @Test
    fun parse_missingPairingToken_returnsError() {
        val result = NovuPairingDeepLinkParser.parse(
            "llamalabels://novu-pair?app=my-app&sub=subscriber-123&hash=secureHash",
        )

        assertTrue(result is PairingParseResult.Error)
        assertEquals("Missing pairing token", (result as PairingParseResult.Error).reason)
    }
}
