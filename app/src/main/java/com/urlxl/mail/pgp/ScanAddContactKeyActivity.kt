package com.urlxl.mail.pgp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.urlxl.mail.R
import com.urlxl.mail.applyPrimaryButtonTheme
import com.urlxl.mail.applyThemeToActivity
import com.urlxl.mail.applyTopInsetWithHeader
import com.urlxl.mail.contacts.ContactsListActivity
import com.urlxl.mail.contacts.ContactsRuntime
import com.urlxl.mail.contacts.toDto
import com.urlxl.mail.data.DataRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * "Scan to Add Contact Key": reuses [com.urlxl.mail.push.PushPairingActivity]'s scan pattern
 * (`GmsBarcodeScanning.getClient(this).startScan().await()`) to decode a `/api/pgp/qr/key`
 * URL, fetches the key via [PgpQrClient.fetchKey] (unauthenticated — the token is the
 * credential), shows the fingerprint for out-of-band confirmation, then lets the user pick an
 * existing contact (via [ContactsListActivity] in pick mode) to save the key onto.
 *
 * Saving does NOT go through a per-contact REST endpoint — this app never calls those. It
 * follows [com.urlxl.mail.contacts.ContactEditActivity.save]'s exact pattern instead:
 * `queueUpdate` on the existing [com.urlxl.mail.contacts.ContactDto] with `pgpKey` set, then
 * `syncNowAsync()`.
 */
class ScanAddContactKeyActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var nameText: TextView
    private lateinit var fingerprintText: TextView
    private lateinit var confirmButton: Button
    private lateinit var scanButton: Button

    private val client = PgpQrClient()
    private var pendingKey: PgpQrKeyDto? = null

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uid = if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(ContactsListActivity.EXTRA_RESULT_UID)
        } else {
            null
        }
        if (!uid.isNullOrBlank()) {
            saveKeyToContact(uid)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_add_contact_key)
        setTitle(R.string.pgp_qr_scan_title)
        applyThemeToActivity(this)
        applyTopInsetWithHeader(this, findViewById(R.id.scanAddContactKeyRoot))

        statusText = findViewById(R.id.pgpScanStatusText)
        nameText = findViewById(R.id.pgpScanNameText)
        fingerprintText = findViewById(R.id.pgpScanFingerprintText)
        confirmButton = findViewById(R.id.btnConfirmFingerprint)
        scanButton = findViewById(R.id.btnScanPgpQr)

        scanButton.setOnClickListener { scanQr() }
        confirmButton.setOnClickListener { onFingerprintConfirmed() }
    }

    override fun onResume() {
        super.onResume()
        applyThemeToActivity(this)
        applyPrimaryButtonTheme(this, scanButton)
        applyPrimaryButtonTheme(this, confirmButton)
    }

    private fun scanQr() {
        lifecycleScope.launch {
            runCatching {
                GmsBarcodeScanning.getClient(this@ScanAddContactKeyActivity).startScan().await().rawValue.orEmpty()
            }.onSuccess { raw ->
                if (raw.isNotBlank()) {
                    handleScanned(raw)
                }
            }.onFailure {
                Toast.makeText(this@ScanAddContactKeyActivity, R.string.pgp_qr_scan_canceled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleScanned(raw: String) {
        resetConfirmationState()
        val parsed = parsePgpQrKeyUrl(raw)
        if (parsed == null) {
            statusText.text = getString(R.string.pgp_qr_scan_invalid)
            scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
            return
        }

        statusText.text = getString(R.string.pgp_qr_my_code_loading)
        lifecycleScope.launch {
            when (val result = client.fetchKey(parsed.serverUrl, parsed.token)) {
                is PgpQrKeyResult.Success -> showFetchedKey(result.key)
                is PgpQrKeyResult.Forbidden -> {
                    statusText.text = getString(R.string.pgp_qr_scan_forbidden)
                    scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
                }
                is PgpQrKeyResult.NotFound -> {
                    statusText.text = getString(R.string.pgp_qr_scan_not_found)
                    scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
                }
                is PgpQrKeyResult.ServiceUnavailable -> {
                    statusText.text = getString(R.string.pgp_qr_scan_unavailable)
                    scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
                }
                is PgpQrKeyResult.Retryable -> {
                    statusText.text = result.message
                    scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
                }
            }
        }
    }

    private fun showFetchedKey(key: PgpQrKeyDto) {
        pendingKey = key
        statusText.text = getString(R.string.pgp_qr_scan_confirm_prompt)
        nameText.text = getString(R.string.pgp_qr_scan_name_label, key.name)
        nameText.visibility = View.VISIBLE
        fingerprintText.text = getString(R.string.pgp_qr_scan_fingerprint_label, key.fingerprint)
        fingerprintText.visibility = View.VISIBLE
        confirmButton.visibility = View.VISIBLE
        scanButton.setText(R.string.pgp_qr_scan_scan_again_button)
    }

    private fun resetConfirmationState() {
        pendingKey = null
        nameText.visibility = View.GONE
        fingerprintText.visibility = View.GONE
        confirmButton.visibility = View.GONE
    }

    private fun onFingerprintConfirmed() {
        if (pendingKey == null) return
        pickContactLauncher.launch(
            Intent(this, ContactsListActivity::class.java).putExtra(ContactsListActivity.EXTRA_PICK_MODE, true),
        )
    }

    private fun saveKeyToContact(uid: String) {
        val key = pendingKey ?: return
        lifecycleScope.launch {
            val entity = DataRuntime.graph(this@ScanAddContactKeyActivity).database.contactDao().getByUid(uid)
            if (entity == null) {
                Toast.makeText(this@ScanAddContactKeyActivity, R.string.pgp_qr_scan_invalid, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dto = entity.toDto().copy(pgpKey = key.publicKey)

            val graph = ContactsRuntime.graph(this@ScanAddContactKeyActivity)
            graph.repository.queueUpdate(dto)
            graph.coordinator.syncNowAsync()

            Toast.makeText(this@ScanAddContactKeyActivity, R.string.pgp_qr_scan_saved, Toast.LENGTH_SHORT).show()
            resetConfirmationState()
            statusText.text = ""
            scanButton.setText(R.string.pgp_qr_scan_scan_button)
        }
    }

    companion object {
        /** Parses a decoded QR payload into the `(serverUrl, token)` pair [PgpQrClient.fetchKey]
         *  expects. Returns null unless the payload is an `.../api/pgp/qr/key?t=...` URL. */
        internal fun parsePgpQrKeyUrl(raw: String): ParsedPgpQrKeyUrl? {
            val url = raw.trim().toHttpUrlOrNull() ?: return null
            if (url.encodedPath != "/api/pgp/qr/key") return null
            val token = url.queryParameter("t")?.takeIf { it.isNotBlank() } ?: return null
            val serverUrl = HttpUrl.Builder()
                .scheme(url.scheme)
                .host(url.host)
                .port(url.port)
                .build()
                .toString()
                .trimEnd('/')
            return ParsedPgpQrKeyUrl(serverUrl = serverUrl, token = token)
        }
    }
}

internal data class ParsedPgpQrKeyUrl(val serverUrl: String, val token: String)
