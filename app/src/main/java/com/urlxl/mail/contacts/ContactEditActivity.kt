package com.urlxl.mail.contacts

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.R
import com.urlxl.mail.applyPrimaryButtonTheme
import com.urlxl.mail.applyThemeToActivity
import com.urlxl.mail.applyTopInsetWithHeader
import com.urlxl.mail.data.DataRuntime
import kotlinx.coroutines.launch

/** Create/edit form. Only fn is required per Mobile_Contact_Sync.md's field table; everything
 *  else is optional. A contact may carry more than one email/phone (set elsewhere, e.g. the web
 *  UI or CardDAV) — this single-field editor preserves any entries beyond the first untouched
 *  rather than silently dropping them on save. */
class ContactEditActivity : AppCompatActivity() {

    private lateinit var fnField: EditText
    private lateinit var orgField: EditText
    private lateinit var emailField: EditText
    private lateinit var phoneField: EditText
    private lateinit var notesField: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button

    private var existingUid: String = ""
    private var existingRev: Long = 0
    private var extraEmails: List<ContactFieldDto> = emptyList()
    private var extraPhones: List<ContactFieldDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_edit)
        applyThemeToActivity(this)
        applyTopInsetWithHeader(this, findViewById(R.id.contactEditRoot))
        setTitle(R.string.contacts_edit_title)

        fnField = findViewById(R.id.editContactName)
        orgField = findViewById(R.id.editContactOrg)
        emailField = findViewById(R.id.editContactEmail)
        phoneField = findViewById(R.id.editContactPhone)
        notesField = findViewById(R.id.editContactNotes)
        saveButton = findViewById(R.id.btnSaveContact)
        deleteButton = findViewById(R.id.btnDeleteContact)

        applyPrimaryButtonTheme(this, saveButton)
        applyPrimaryButtonTheme(this, deleteButton)

        existingUid = intent.getStringExtra(EXTRA_UID).orEmpty()
        if (existingUid.isBlank()) {
            deleteButton.visibility = View.GONE
        } else {
            loadExisting(existingUid)
        }

        saveButton.setOnClickListener { save() }
        deleteButton.setOnClickListener { delete() }
    }

    override fun onResume() {
        super.onResume()
        applyThemeToActivity(this)
        applyPrimaryButtonTheme(this, saveButton)
        applyPrimaryButtonTheme(this, deleteButton)
    }

    private fun loadExisting(uid: String) {
        lifecycleScope.launch {
            val entity = DataRuntime.graph(this@ContactEditActivity).database.contactDao().getByUid(uid) ?: return@launch
            val dto = entity.toDto()
            existingRev = dto.rev
            fnField.setText(dto.fn)
            orgField.setText(dto.org.orEmpty())
            notesField.setText(dto.notes.orEmpty())
            emailField.setText(dto.emails.firstOrNull()?.value.orEmpty())
            phoneField.setText(dto.phones.firstOrNull()?.value.orEmpty())
            extraEmails = dto.emails.drop(1)
            extraPhones = dto.phones.drop(1)
        }
    }

    private fun save() {
        val fn = fnField.text.toString().trim()
        if (fn.isBlank()) {
            Toast.makeText(this, R.string.contacts_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val email = emailField.text.toString().trim()
        val phone = phoneField.text.toString().trim()
        val emails = (if (email.isNotBlank()) listOf(ContactFieldDto(value = email)) else emptyList()) + extraEmails
        val phones = (if (phone.isNotBlank()) listOf(ContactFieldDto(value = phone)) else emptyList()) + extraPhones

        val dto = ContactDto(
            uid = existingUid,
            rev = existingRev,
            fn = fn,
            org = orgField.text.toString().trim().ifBlank { null },
            notes = notesField.text.toString().trim().ifBlank { null },
            emails = emails,
            phones = phones,
        )

        lifecycleScope.launch {
            val graph = ContactsRuntime.graph(this@ContactEditActivity)
            if (existingUid.isBlank()) {
                graph.repository.queueCreate(dto)
            } else {
                graph.repository.queueUpdate(dto)
            }
            graph.coordinator.syncNowAsync()
            Toast.makeText(this@ContactEditActivity, R.string.contacts_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun delete() {
        lifecycleScope.launch {
            val graph = ContactsRuntime.graph(this@ContactEditActivity)
            graph.repository.queueDelete(existingUid, existingRev)
            graph.coordinator.syncNowAsync()
            finish()
        }
    }

    companion object {
        const val EXTRA_UID = "contact_uid"
    }
}
