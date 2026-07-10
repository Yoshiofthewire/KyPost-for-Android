package com.urlxl.mail.push

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.R
import kotlinx.coroutines.launch

class MfaApprovalActivity : AppCompatActivity() {
    private var challengeId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa_approval)

        challengeId = intent.getStringExtra(PushNotificationDispatcher.EXTRA_MFA_CHALLENGE_ID).orEmpty()
        if (challengeId.isBlank()) {
            finish()
            return
        }

        findViewById<Button>(R.id.btnMfaApprove).setOnClickListener { resolve(approve = true) }
        findViewById<Button>(R.id.btnMfaDeny).setOnClickListener { resolve(approve = false) }
    }

    private fun resolve(approve: Boolean) {
        findViewById<Button>(R.id.btnMfaApprove).isEnabled = false
        findViewById<Button>(R.id.btnMfaDeny).isEnabled = false
        lifecycleScope.launch {
            MfaResponseReceiver.respond(applicationContext, challengeId, approve)
            finish()
        }
    }
}
