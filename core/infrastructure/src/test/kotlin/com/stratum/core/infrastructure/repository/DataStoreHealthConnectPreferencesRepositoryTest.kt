package com.stratum.core.infrastructure.repository

import com.stratum.core.infrastructure.healthconnect.HealthConnectPreferencesRecordSerializer
import com.stratum.domain.healthconnect.HealthFailureKind
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreHealthConnectPreferencesRepository]. The DataStore file is
 * process-scoped — each test resets preference state in [setup]. Asserts values only; the
 * repository never logs, so no logging fake appears anywhere near it.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreHealthConnectPreferencesRepository")
class DataStoreHealthConnectPreferencesRepositoryTest {

    private lateinit var repository: DataStoreHealthConnectPreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        repository = DataStoreHealthConnectPreferencesRepository(RuntimeEnvironment.getApplication())
        repository.setEnabled(false)
        repository.setPendingSyncError(null)
    }

    @Test
    @DisplayName("setEnabled persists the preference")
    fun setEnabledPersists() = runTest {
        repository.setEnabled(true)

        assertEquals(true, repository.getPreferences().enabled)
    }

    @Test
    @DisplayName("setPendingSyncError persists the sticky kind")
    fun setPendingSyncErrorPersists() = runTest {
        repository.setPendingSyncError(HealthFailureKind.PermissionDenied)

        assertEquals(
            HealthFailureKind.PermissionDenied,
            repository.getPreferences().pendingSyncError,
        )
    }

    @Test
    @DisplayName("clearing the sticky error with null persists null")
    fun clearPendingSyncErrorPersists() = runTest {
        repository.setPendingSyncError(HealthFailureKind.Unavailable)

        repository.setPendingSyncError(null)

        assertEquals(null, repository.getPreferences().pendingSyncError)
    }

    @Test
    @DisplayName("legacy free-text record decodes with enabled preserved and no pending error")
    fun legacyFreeTextRecordDropsErrorKeepsEnabled() = runTest {
        val legacy =
            """{"enabled":true,"pendingSyncError":"<vendor text>"}"""

        val record =
            HealthConnectPreferencesRecordSerializer.readFrom(
                ByteArrayInputStream(legacy.toByteArray()),
            )
        val prefs = record.toDomain()

        assertEquals(true, prefs.enabled)
        assertNull(prefs.pendingSyncError)
    }

    @Test
    @DisplayName("unrecognized pendingSyncErrorKind maps to Unknown")
    fun unknownKindNameMapsToUnknown() = runTest {
        val record =
            HealthConnectPreferencesRecordSerializer.readFrom(
                ByteArrayInputStream(
                    """{"enabled":false,"pendingSyncErrorKind":"Bogus"}""".toByteArray(),
                ),
            )

        assertEquals(HealthFailureKind.Unknown, record.toDomain().pendingSyncError)
    }

    @Test
    @DisplayName("recognized pendingSyncErrorKind round-trips the kind")
    fun knownKindNameMapsToKind() = runTest {
        val record =
            HealthConnectPreferencesRecordSerializer.readFrom(
                ByteArrayInputStream(
                    """{"enabled":true,"pendingSyncErrorKind":"Unavailable"}""".toByteArray(),
                ),
            )
        val prefs = record.toDomain()

        assertEquals(true, prefs.enabled)
        assertEquals(HealthFailureKind.Unavailable, prefs.pendingSyncError)
    }
}
