package com.urlxl.mail.push

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurePairingStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val pairing = PairingData(
        subscriberId = "subscriber-id",
        subscriberHash = "top-secret-subscriber-hash",
        serverUrl = "https://server.example.com",
        registrationUrl = "https://server.example.com/api/notifications/native/register",
        pairingToken = "top-secret-pairing-token",
        deviceId = "resolved-device-id",
        pairedAtEpochMs = 1_000L,
    )

    @Before
    fun clearAnyExistingState() {
        runBlocking { SecurePairingStore(context).clearPairing() }
    }

    @Test
    fun savePairing_thenReload_roundTripsAllFields() = runBlocking {
        SecurePairingStore(context).savePairing(pairing)

        // A fresh instance must read the same persisted (decrypted) data back.
        val reloaded = SecurePairingStore(context).pairing.value

        assertEquals(pairing, reloaded)
    }

    @Test
    fun clearPairing_removesPersistedData() = runBlocking {
        val store = SecurePairingStore(context)
        store.savePairing(pairing)
        store.clearPairing()

        assertNull(SecurePairingStore(context).pairing.value)
    }

    @Test
    fun underlyingPrefsFile_doesNotContainPlaintextSecrets() = runBlocking {
        SecurePairingStore(context).savePairing(pairing)

        val prefsFile = File(context.filesDir.parentFile, "shared_prefs/push_pairing_secure.xml")
        assertTrue("expected encrypted prefs file to exist", prefsFile.exists())

        val rawContents = prefsFile.readText()
        assertFalse(rawContents.contains(pairing.subscriberHash))
        assertFalse(rawContents.contains(pairing.pairingToken))
        assertFalse(rawContents.contains(pairing.subscriberId))
    }
}
