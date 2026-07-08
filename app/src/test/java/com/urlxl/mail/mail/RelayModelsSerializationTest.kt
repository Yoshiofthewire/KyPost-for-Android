package com.urlxl.mail.mail

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayModelsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun relayMailRequestDto_toCcBcc_serializeAsPlainStrings_notArrays() {
        val dto = RelayMailRequestDto(
            to = "a@example.com, b@example.com",
            cc = "c@example.com",
            subject = "Hi",
            body = "Body",
        )

        val encoded = json.encodeToString(dto)

        assertTrue(encoded.contains("\"to\":\"a@example.com, b@example.com\""))
        assertFalse(encoded.contains("\"to\":["))
        assertFalse(encoded.contains("\"cc\":["))
    }

    @Test
    fun relayInboxResponseDto_decodesByTabMap() {
        val jsonText = """
            {
              "tabs": ["Work", "Personal"],
              "byTab": {
                "Work": [{"messageId": "m1", "sender": "a@example.com", "subject": "S", "body": "B", "label": "Work", "status": "unread"}],
                "Personal": []
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<RelayInboxResponseDto>(jsonText)

        assertEquals(listOf("Work", "Personal"), parsed.tabs)
        assertEquals(1, parsed.byTab["Work"]?.size)
        assertEquals("m1", parsed.byTab["Work"]?.first()?.messageId)
    }
}
