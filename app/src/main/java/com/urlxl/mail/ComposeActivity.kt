package com.urlxl.mail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.urlxl.mail.mail.MailDraft
import com.urlxl.mail.mail.MailOutcome
import com.urlxl.mail.mail.MailRuntime
import com.urlxl.mail.mail.userFacingMessage
import java.util.concurrent.Executors

class ComposeActivity : AppCompatActivity() {

    private lateinit var toField: EditText
    private lateinit var subjectField: EditText
    private lateinit var bodyField: EditText
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose)
        applyThemeToActivity(this)

        val root = findViewById<android.view.View>(R.id.composeRoot)
        applyTopInsetWithHeader(this, root)

        setTitle(R.string.compose_email)

        toField = findViewById(R.id.composeToField)
        subjectField = findViewById(R.id.composeSubjectField)
        bodyField = findViewById(R.id.composeBodyField)
        sendButton = findViewById(R.id.composeSendButton)
        cancelButton = findViewById(R.id.composeCancelButton)

        toField.setText(intent.getStringExtra(EXTRA_TO).orEmpty())
        subjectField.setText(intent.getStringExtra(EXTRA_SUBJECT).orEmpty())
        val prefillBody = intent.getStringExtra(EXTRA_BODY).orEmpty()
        bodyField.setText(prefillBody)
        if (prefillBody.isNotEmpty()) {
            bodyField.setSelection(0)
        }

        sendButton.setOnClickListener { sendEmail() }
        cancelButton.setOnClickListener { finish() }
        applyPrimaryButtonTheme(this, sendButton)
        applyPrimaryButtonTheme(this, cancelButton)
    }

    override fun onResume() {
        super.onResume()
        applyThemeToActivity(this)
        applyPrimaryButtonTheme(this, sendButton)
        applyPrimaryButtonTheme(this, cancelButton)
    }

    private fun sendEmail() {
        val to = toField.text.toString().trim()
        val subject = subjectField.text.toString().trim()
        val body = bodyField.text.toString().trim()

        if (to.isBlank() || subject.isBlank() || body.isBlank()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        ioExecutor.execute {
            val outcome = MailRuntime.graph(this).repository.send(MailDraft(to = to, subject = subject, body = body))
            runOnUiThread {
                when (outcome) {
                    is MailOutcome.Success -> {
                        val warning = outcome.value.warning
                        // The send already succeeded even when sentSaved is false — surface the
                        // warning as a non-blocking notice, not a failure.
                        val message = warning.ifBlank { "Email sent successfully" }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    else -> {
                        sendButton.isEnabled = true
                        sendButton.text = getString(R.string.compose_send)
                        Toast.makeText(this, outcome.userFacingMessage(), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }

    companion object {
        const val EXTRA_TO = "compose_to"
        const val EXTRA_SUBJECT = "compose_subject"
        const val EXTRA_BODY = "compose_body"
    }
}
