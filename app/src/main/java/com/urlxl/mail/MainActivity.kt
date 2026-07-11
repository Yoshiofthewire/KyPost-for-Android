package com.urlxl.mail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.push.MfaApprovalActivity
import com.urlxl.mail.push.MfaChallengePayloadParser
import com.urlxl.mail.push.PushNotificationDispatcher
import com.urlxl.mail.push.PushRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        lifecycleScope.launch {
            intent.extras?.let { extras ->
                MfaChallengePayloadParser.parse(extras)?.let { mfa ->
                    val mfaIntent = Intent(this@MainActivity, MfaApprovalActivity::class.java)
                    mfaIntent.putExtra(PushNotificationDispatcher.EXTRA_MFA_CHALLENGE_ID, mfa.challengeId)
                    startActivity(mfaIntent)
                    finish()
                    return@launch
                }
            }

            val configured = PushRuntime.graph(this@MainActivity).repository.state.first().pairing != null

            val targetIntent = if (configured) {
                Intent(this@MainActivity, InboxActivity::class.java).apply {
                    val msgId = intent.getStringExtra(PushNotificationDispatcher.EXTRA_MESSAGE_ID)
                    if (msgId != null) {
                        putExtra(PushNotificationDispatcher.EXTRA_MESSAGE_ID, msgId)
                        putExtra(PushNotificationDispatcher.EXTRA_SENDER, intent.getStringExtra(PushNotificationDispatcher.EXTRA_SENDER))
                        putExtra(PushNotificationDispatcher.EXTRA_SUBJECT, intent.getStringExtra(PushNotificationDispatcher.EXTRA_SUBJECT))
                    }
                }
            } else {
                Intent(this@MainActivity, com.urlxl.mail.push.PushPairingActivity::class.java)
            }
            startActivity(targetIntent)
            finish()
        }
    }
}
