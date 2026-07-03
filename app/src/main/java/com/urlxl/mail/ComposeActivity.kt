package com.urlxl.mail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            try {
                val mailSettings = MailSettings(this)
                val mailGateway = MailGateway(mailSettings.getConfig())
                mailGateway.sendEmail(to, subject, body)

                runOnUiThread {
                    Toast.makeText(this, "Email sent successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    sendButton.isEnabled = true
                    sendButton.text = getString(R.string.compose_send)
                    Toast.makeText(this, "Failed to send email: ${ex.message}", Toast.LENGTH_SHORT).show()
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
