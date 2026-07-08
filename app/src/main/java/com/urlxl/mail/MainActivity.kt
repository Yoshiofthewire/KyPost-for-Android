package com.urlxl.mail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.push.PushRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mailSettings = MailSettings(this)

        lifecycleScope.launch {
            // Manual IMAP mode is "configured" once host/user/pass are filled in; Relay mode is
            // "configured" once the device is paired — checking isConfigured() alone here would
            // trap a paired, relay-only user in a loop back to Settings since they never fill IMAP
            // fields.
            val configured = when (mailSettings.getConnectionMode()) {
                MailConnectionMode.RELAY -> PushRuntime.graph(this@MainActivity).repository.state.first().pairing != null
                MailConnectionMode.MANUAL_IMAP -> mailSettings.isConfigured()
            }

            val intent = if (configured) {
                Intent(this@MainActivity, InboxActivity::class.java)
            } else {
                Intent(this@MainActivity, SettingsActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }
}
