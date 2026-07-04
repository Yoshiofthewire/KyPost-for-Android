package com.urlxl.mail.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayRequestMapperTest {

    @Test
    fun map_usesSubscriberIdPairingTokenAndDeviceToken() {
        val pairing = PairingData(
            applicationIdentifier = "app-id",
            subscriberId = "subscriber-id",
            subscriberHash = "subscriber-hash",
            apiBase = "https://api.novu.co",
            serverUrl = "https://server.example.com",
            relayUrl = "https://server.example.com/api/notifications/novu/relay/fcm",
            pairingToken = "pairing-token",
            pairedAtEpochMs = 100L,
        )

        val request = RelayRequestMapper.map(pairing = pairing, token = "fcm-token")

        assertEquals("subscriber-id", request.subscriberId)
        assertEquals("pairing-token", request.pairingToken)
        assertEquals("subscriber-hash", request.subscriberHash)
        assertEquals("fcm-token", request.deviceToken)
    }

    @Test
    fun map_blankSubscriberHash_becomesNull() {
        val pairing = PairingData(
            applicationIdentifier = "app-id",
            subscriberId = "subscriber-id",
            subscriberHash = "",
            apiBase = "https://api.novu.co",
            serverUrl = null,
            relayUrl = "https://server.example.com/api/notifications/novu/relay/fcm",
            pairingToken = "pairing-token",
            pairedAtEpochMs = 100L,
        )

        val request = RelayRequestMapper.map(pairing = pairing, token = "fcm-token")

        assertNull(request.subscriberHash)
    }
}
