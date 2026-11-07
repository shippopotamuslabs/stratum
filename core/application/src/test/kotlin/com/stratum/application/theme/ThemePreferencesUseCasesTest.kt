package com.stratum.application.theme

import com.stratum.core.testing.logging.FakeLoggingPort
import app.cash.turbine.test
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

class ThemePreferencesUseCasesTest {

    private val fakeRepository = FakeThemePreferencesRepository()
    private lateinit var observe: ObserveThemePreferencesUseCase
    private lateinit var setFamily: SetThemeFamilyUseCase
    private lateinit var setColorMode: SetColorModeUseCase

    @BeforeEach
    fun setup() {
        fakeRepository.seed(ThemePreferences())
        observe = ObserveThemePreferencesUseCase(fakeRepository)
        setFamily = SetThemeFamilyUseCase(fakeRepository, FakeLoggingPort())
        setColorMode = SetColorModeUseCase(fakeRepository, FakeLoggingPort())
    }

    @Test
    @DisplayName("observe emits repository stream")
    fun observeEmits() = runTest {
        observe().test {
            assertEquals(ThemePreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setFamily writes NeoBrutalist")
    fun setFamilyWrites() = runTest {
        setFamily(ThemeFamily.NeoBrutalist)
        assertEquals(ThemeFamily.NeoBrutalist, fakeRepository.current.family)
    }

    @Test
    @DisplayName("setColorMode writes Dark")
    fun setColorModeWrites() = runTest {
        setColorMode(ColorMode.Dark)
        assertEquals(ColorMode.Dark, fakeRepository.current.colorMode)
    }

    @Test
    @DisplayName("setFamily returns PersistenceFailed when the repository write throws (STR-243)")
    fun setFamilyPersistenceFailure() = runTest {
        val throwing =
            object : ThemePreferencesRepository by fakeRepository {
                override suspend fun setFamily(family: ThemeFamily) {
                    throw RuntimeException("store unavailable")
                }
            }
        val result = SetThemeFamilyUseCase(throwing, FakeLoggingPort())(ThemeFamily.NeoBrutalist)
        assertEquals(ThemePreferenceResult.PersistenceFailed, result)
    }

    @Test
    @DisplayName("setColorMode propagates CancellationException instead of capturing it (STR-243)")
    fun setColorModeCancellationPropagates() {
        val cancelling =
            object : ThemePreferencesRepository by fakeRepository {
                override suspend fun setColorMode(colorMode: ColorMode) {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
        val subject = SetColorModeUseCase(cancelling, FakeLoggingPort())
        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking { subject(ColorMode.Dark) }
        }
    }
}
