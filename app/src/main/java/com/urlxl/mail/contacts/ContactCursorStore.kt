package com.urlxl.mail.contacts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.contactsDataStore by preferencesDataStore(name = "contacts_state")

private val KEY_CURSOR = longPreferencesKey("contacts_cursor")
private val KEY_CURSOR_SUB = stringPreferencesKey("contacts_cursor_sub")

/**
 * Durable per-subscriber sync cursor, mirroring [com.urlxl.mail.push.PushRepository]'s pull-cursor
 * pattern exactly: scoped to the subscriber so re-pairing as someone else starts clean.
 */
class ContactCursorStore(private val context: Context) {
    suspend fun cursor(subscriberId: String): Long {
        val prefs = context.contactsDataStore.data
            .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
            .first()
        return if (prefs[KEY_CURSOR_SUB] == subscriberId) prefs[KEY_CURSOR] ?: 0L else 0L
    }

    suspend fun advanceCursor(subscriberId: String, cursor: Long) {
        context.contactsDataStore.edit { prefs ->
            val current = if (prefs[KEY_CURSOR_SUB] == subscriberId) prefs[KEY_CURSOR] ?: 0L else 0L
            prefs[KEY_CURSOR_SUB] = subscriberId
            prefs[KEY_CURSOR] = maxOf(current, cursor)
        }
    }

    /** Used for tooOld handling: discard the cursor so the next sync does a full since=0 pull. */
    suspend fun resetCursor(subscriberId: String) {
        context.contactsDataStore.edit { prefs ->
            prefs[KEY_CURSOR_SUB] = subscriberId
            prefs[KEY_CURSOR] = 0L
        }
    }
}
