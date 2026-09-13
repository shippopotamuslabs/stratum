@file:Suppress("ForbiddenMockKInTests") // Firebase Remote Config SDK boundary — no fake available.

package com.stratum.core.data.featureflag

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.featureflag.FeatureFlag
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FirebaseFeatureFlagRepository

    @BeforeEach
    fun setUp() {
        repository = FirebaseFeatureFlagRepository(
            remoteConfig = remoteConfig,
            loggingPort = fakeLogging,
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
}
