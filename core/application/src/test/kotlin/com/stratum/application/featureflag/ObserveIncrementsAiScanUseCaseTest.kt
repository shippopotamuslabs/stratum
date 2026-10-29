package com.stratum.application.featureflag

import app.cash.turbine.test
import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.featureflag.AppFeatureFlag

class ObserveIncrementsAiScanUseCaseTest {

    private val fakeFlags = FakeFeatureFlagRepository()
    private val observeFlag = ObserveIncrementsAiScanUseCase(fakeFlags)

    @Test
    @DisplayName("Given the flag off when observed then false is emitted")
    fun emitsFalseByDefault() = runTest {
        observeFlag().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given the flag enabled when observed then true is emitted")
    fun emitsTrueWhenEnabled() = runTest {
        fakeFlags.setFlag(AppFeatureFlag.INCREMENTS_AI_SCAN, true)

        observeFlag().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
