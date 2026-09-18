@file:Suppress("ForbiddenMockKInTests") // Firebase Remote Config SDK boundary — no fake available.

package com.stratum.core.data.featureflag

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.domain.featureflag.FeatureFlag
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class FirebaseFeatureFlagRepositoryTest {

    private enum class TestFeatureFlag(
        override val key: String,
        override val description: String,
    ) : FeatureFlag {
        SAMPLE_FLAG(key = "enable_sample_flag", description = "Sample test flag"),
    }

    private val remoteConfig: FirebaseRemoteConfig = mockk(relaxed = true)
    private val fakeLogging = FakeLoggingPort()
    private val fakeAnalytics = FakeAnalyticsPort()
    private val fakeCrashTelemetry = FakeCrashTelemetryPort()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FirebaseFeatureFlagRepository

    @BeforeEach
    fun setUp() {
        repository = FirebaseFeatureFlagRepository(
            remoteConfig = remoteConfig,
            loggingPort = fakeLogging,
            analyticsPort = fakeAnalytics,
            crashTelemetryPort = fakeCrashTelemetry,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    @DisplayName("Given unconfigured flag when isEnabled checked then returns false by default")
    fun returnsFalseByDefault() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns false

        assertFalse(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))
    }

    @Test
    @DisplayName("Given remote config has flag enabled when isEnabled checked then returns true")
    fun returnsRemoteConfigValueWhenNoOverride() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns true

        assertTrue(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))
    }

    @Test
    @DisplayName("Given remote config throws exception when isEnabled checked then catches and returns false")
    fun returnsFalseWhenRemoteConfigThrows() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } throws RuntimeException("Remote config error")

        assertFalse(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))
    }

    @Test
    @DisplayName("Given dev override set when isEnabled checked then override takes precedence")
    fun devOverridePrecedesRemoteConfig() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns false

        repository.setDevOverride(TestFeatureFlag.SAMPLE_FLAG, true)
        assertTrue(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))

        repository.setDevOverride(TestFeatureFlag.SAMPLE_FLAG, false)
        assertFalse(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))
    }

    @Test
    @DisplayName("Given dev override cleared when isEnabled checked then falls back to remote config")
    fun clearDevOverridesRestoresRemoteConfig() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns true

        repository.setDevOverride(TestFeatureFlag.SAMPLE_FLAG, false)
        assertFalse(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))

        repository.clearDevOverrides()
        assertTrue(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))
    }

    @Test
    @DisplayName("Given remote config fetch succeeds when syncFlags called then returns true")
    fun syncFlagsSucceeds() = runTest(testDispatcher) {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)

        val result = repository.syncFlags()

        assertTrue(result)
    }

    @Test
    @DisplayName("Given remote config fetch fails when syncFlags called then catches and returns false")
    fun syncFlagsHandlesFailure() = runTest(testDispatcher) {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forException(RuntimeException("Network error"))

        val result = repository.syncFlags()

        assertFalse(result)
    }

    @Test
    @DisplayName("Given transient fetch failures when syncFlags called then retries and returns true on success")
    fun syncFlagsRetriesTransientFailures() = runTest(testDispatcher) {
        every { remoteConfig.fetchAndActivate() } returnsMany
            listOf(
                Tasks.forException<Boolean>(RuntimeException("flaky network")),
                Tasks.forResult(true),
            )

        val result = repository.syncFlags()

        assertTrue(result)
    }

    @Test
    @DisplayName("Given persistent fetch failures when syncFlags called then gives up after three attempts")
    fun syncFlagsGivesUpAfterMaxAttempts() = runTest(testDispatcher) {
        var calls = 0
        every { remoteConfig.fetchAndActivate() } answers {
            calls++
            Tasks.forException<Boolean>(RuntimeException("Network error"))
        }

        val result = repository.syncFlags()

        assertFalse(result)
        assertEquals(3, calls)
    }

    @Test
    @DisplayName("Given throttled fetch when syncFlags called then returns false without retrying")
    fun syncFlagsDoesNotRetryWhenThrottled() = runTest(testDispatcher) {
        // MockK instantiation bypasses the SDK constructor, which needs Android TextUtils.
        val throttled = mockk<FirebaseRemoteConfigFetchThrottledException>()
        every { throttled.message } returns "Fetch was throttled."
        var calls = 0
        every { remoteConfig.fetchAndActivate() } answers {
            calls++
            Tasks.forException<Boolean>(throttled)
        }

        val result = repository.syncFlags()

        assertFalse(result)
        assertEquals(1, calls)
        assertTrue(fakeLogging.warnings.single().message.contains("throttled", ignoreCase = true))
    }

    @Test
    @DisplayName("Given cancelled task when syncFlags called then returns false without retrying")
    fun syncFlagsDoesNotRetryWhenCancelled() = runTest(testDispatcher) {
        var calls = 0
        every { remoteConfig.fetchAndActivate() } answers {
            calls++
            Tasks.forCanceled<Boolean>()
        }

        val result = repository.syncFlags()

        assertFalse(result)
        assertEquals(1, calls)
    }

    @Test
    @DisplayName("Given coroutine cancelled during backoff when syncFlags called then cancellation propagates")
    fun syncFlagsPropagatesCoroutineCancellation() = runTest(testDispatcher) {
        every { remoteConfig.fetchAndActivate() } returns Tasks.forException(RuntimeException("Network error"))

        val deferred = async(testDispatcher) { repository.syncFlags() }
        runCurrent()
        deferred.cancel()

        assertThrows<kotlinx.coroutines.CancellationException> {
            deferred.await()
        }
        assertTrue(deferred.isCancelled)
    }

    @Test
    @DisplayName("Given first evaluation when isEnabled checked then records analytics exposure and flag state")
    fun firstEvaluationRecordsExposure() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns true

        assertTrue(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))

        val event = fakeAnalytics.events.single()
        assertEquals(FirebaseFeatureFlagRepository.FLAG_EXPOSURE_EVENT, event.name)
        assertEquals(TestFeatureFlag.SAMPLE_FLAG.key, event.params["flag_key"])
        assertEquals("true", event.params["enabled"])
        assertEquals(true, fakeCrashTelemetry.flagStates[TestFeatureFlag.SAMPLE_FLAG.key])
    }

    @Test
    @DisplayName("Given repeat evaluations when isEnabled checked then exposure is recorded once")
    fun repeatEvaluationsRecordExposureOnce() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } returns true

        repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG)
        repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG)

        assertEquals(1, fakeAnalytics.events.size)
    }

    @Test
    @DisplayName("Given remote config throws when isEnabled checked then returns false and records exposure")
    fun readFailureRecordsExposureAsDisabled() {
        every { remoteConfig.getBoolean(TestFeatureFlag.SAMPLE_FLAG.key) } throws RuntimeException("Remote config error")

        assertFalse(repository.isEnabled(TestFeatureFlag.SAMPLE_FLAG))

        val event = fakeAnalytics.events.single()
        assertEquals("false", event.params["enabled"])
        assertEquals(false, fakeCrashTelemetry.flagStates[TestFeatureFlag.SAMPLE_FLAG.key])
    }
}
