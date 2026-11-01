package com.stratum.core.infrastructure.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PullCursorRecordTest {
    @Test
    @DisplayName("empty cursor is only zero updatedAt with blank documentId")
    fun emptyCursor() {
        assertTrue(PullCursorRecord.EMPTY.isEmpty())
        assertTrue(PullCursorRecord(0L, "").isEmpty())
        assertFalse(PullCursorRecord(1L, "").isEmpty())
        assertFalse(PullCursorRecord(0L, "doc-a").isEmpty())
    }

    @Test
    @DisplayName("composite cursor holds updatedAt and documentId for startAfter resume")
    fun compositeFields() {
        val cursor = PullCursorRecord(updatedAtEpochMillis = 42L, documentId = "abc")
        assertEquals(42L, cursor.updatedAtEpochMillis)
        assertEquals("abc", cursor.documentId)
        assertFalse(cursor.isEmpty())
    }
}
