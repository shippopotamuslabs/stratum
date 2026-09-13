package com.stratum.core.testing.featureflag

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.featureflag.FeatureFlag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class FakeFeatureFlagRepositoryTest {

    private enum class TestFeatureFlag(
        override val key: String,
        override val description: String,
    ) : FeatureFlag {
        FLAG_A(key = "test_flag_a", description = "Test flag A"),
        FLAG_B(key = "test_flag_b", description = "Test flag B"),
    }

    private lateinit var repository: FakeFeatureFlagRepository

    @BeforeEach
    fun setUp() {
        repository = FakeFeatureFlagRepository()
    }

    @Test
    @DisplayName("Given unconfigured flag when isEnabled checked then returns false by default")
    fun defaultsToFalse() {
        assertFalse(repository.isEnabled(TestFeatureFlag.FLAG_A))
        assertFalse(repository.isEnabled(TestFeatureFlag.FLAG_B))
    }

    @Test
    @DisplayName("Given flag set to true when isEnabled checked then returns true")
    fun setFlagTogglesState() {
        repository.setFlag(TestFeatureFlag.FLAG_A, true)
        assertTrue(repository.isEnabled(TestFeatureFlag.FLAG_A))

        repository.setFlag(TestFeatureFlag.FLAG_A, false)
        assertFalse(repository.isEnabled(TestFeatureFlag.FLAG_A))
    }

    @Test
    @DisplayName("Given active observer when flag changes then emits new values")
    fun observeEmitsUpdates() = runTest {
        repository.observe(TestFeatureFlag.FLAG_A).test {
            assertFalse(awaitItem())

            repository.setFlag(TestFeatureFlag.FLAG_A, true)
            assertTrue(awaitItem())

            repository.setFlag(TestFeatureFlag.FLAG_A, false)
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given multiple flags set when clear called then all reset to false")
    fun clearResetsAllFlags() {
        repository.setFlag(TestFeatureFlag.FLAG_A, true)
        repository.setFlag(TestFeatureFlag.FLAG_B, true)

        repository.clear()

        assertFalse(repository.isEnabled(TestFeatureFlag.FLAG_A))
        assertFalse(repository.isEnabled(TestFeatureFlag.FLAG_B))
    }

    @Test
    @DisplayName("Given syncFlagsResult when syncFlags called then returns configured result")
    fun syncFlagsReturnsResult() = runTest {
        repository.syncFlagsResult = true
        assertTrue(repository.syncFlags())

        repository.syncFlagsResult = false
        assertFalse(repository.syncFlags())
    }
}
