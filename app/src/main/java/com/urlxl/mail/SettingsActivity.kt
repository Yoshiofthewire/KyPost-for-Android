package com.urlxl.mail

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.pgp.MyQrCodeActivity
import com.urlxl.mail.pgp.ScanAddContactKeyActivity
import com.urlxl.mail.push.PushPairingActivity
import com.urlxl.mail.push.PushRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var relayStatusText: TextView
    private lateinit var btnPairDevice: Button
    private lateinit var btnMyQrCode: Button
    private lateinit var btnScanContactKey: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyThemeToActivity(this)

        val root = findViewById<android.view.View>(R.id.settingsRoot)
        applyTopInsetWithHeader(this, root)

        initViews()

        btnPairDevice.setOnClickListener {
            startActivity(Intent(this, PushPairingActivity::class.java))
        }
        btnMyQrCode.setOnClickListener {
            startActivity(Intent(this, MyQrCodeActivity::class.java))
        }
        btnScanContactKey.setOnClickListener {
            startActivity(Intent(this, ScanAddContactKeyActivity::class.java))
        }

        btnSave.setOnClickListener { saveSettings() }
        applyPrimaryButtonTheme(this, btnSave)
        applyPrimaryButtonTheme(this, btnPairDevice)
        applyPrimaryButtonTheme(this, btnMyQrCode)
        applyPrimaryButtonTheme(this, btnScanContactKey)
    }

    override fun onResume() {
        super.onResume()
        applyThemeToActivity(this)
        applyPrimaryButtonTheme(this, btnSave)
        applyPrimaryButtonTheme(this, btnPairDevice)
        applyPrimaryButtonTheme(this, btnMyQrCode)
        applyPrimaryButtonTheme(this, btnScanContactKey)
        refreshPairingStatus()
    }

    private fun initViews() {
        relayStatusText = findViewById(R.id.relayStatusText)
        btnPairDevice = findViewById(R.id.btnPairDevice)
        btnMyQrCode = findViewById(R.id.btnMyQrCode)
        btnScanContactKey = findViewById(R.id.btnScanContactKey)
        btnSave = findViewById(R.id.btnSaveSettings)
    }

    private fun refreshPairingStatus() {
        lifecycleScope.launch {
            val isPaired = PushRuntime.graph(this@SettingsActivity).repository.state.first().pairing != null
            relayStatusText.setText(
                if (isPaired) R.string.connection_mode_relay_paired else R.string.connection_mode_relay_not_paired,
            )
            btnPairDevice.visibility = if (isPaired) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun saveSettings() {
        val isPaired = PushRuntime.graph(this).repository.state
        lifecycleScope.launch {
            val pairing = isPaired.first().pairing
            if (pairing == null) {
                Toast.makeText(this@SettingsActivity, R.string.connection_mode_relay_not_paired, Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }
}
