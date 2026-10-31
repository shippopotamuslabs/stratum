package com.stratum.core.infrastructure.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.testing.roomFirstEmissionTimeout
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class RoomSpecializationPreferenceRepositoryTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomSpecializationPreferenceRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = RoomSpecializationPreferenceRepository(
            database = database,
            dao = database.specializationPreferenceDao(),
            syncTouch = com.stratum.core.infrastructure.sync.testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given an empty preference when observed then Empty is emitted")
    fun emptyPreferenceEmitsEmpty() = runTest {
        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            assertEquals(SpecializationPreference.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a preference when saved then observe emits it with specialized muscles")
    fun savePersistsAndObserveEmits() = runTest {
        val preference = SpecializationPreference(
            levels = mapOf(
                MuscleGroup.Chest to SpecializationLevel.Specialize,
                MuscleGroup.Back to SpecializationLevel.Specialize,
                MuscleGroup.Quad to SpecializationLevel.Specialize,
            ),
        )

        subject.save(preference)

        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            val emitted = awaitItem()
            assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Quad), emitted.specializedMuscles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a saved preference when saved again with fewer muscles then stale rows are replaced")
    fun saveReplacesWholesale() = runTest {
        subject.save(
            SpecializationPreference(
                levels = mapOf(
                    MuscleGroup.Chest to SpecializationLevel.Specialize,
                    MuscleGroup.Back to SpecializationLevel.Specialize,
                    MuscleGroup.Quad to SpecializationLevel.Specialize,
                ),
            ),
        )

        subject.save(
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Hamstring to SpecializationLevel.Specialize),
            ),
        )

        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            val emitted = awaitItem()
            assertEquals(setOf(MuscleGroup.Hamstring), emitted.specializedMuscles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given an empty preference when saved then no rows remain")
    fun emptySaveClearsAll() = runTest {
        subject.save(
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Chest to SpecializationLevel.Specialize),
            ),
        )
        subject.save(SpecializationPreference.Empty)

        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            assertEquals(SpecializationPreference.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a levelFor query when the muscle is unset then it defaults to Maintenance")
    fun unsetMuscleDefaultsToMaintenance() = runTest {
        subject.save(
            SpecializationPreference(
                levels = mapOf(MuscleGroup.Chest to SpecializationLevel.Specialize),
            ),
        )

        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            val emitted = awaitItem()
            assertEquals(SpecializationLevel.Specialize, emitted.levelFor(MuscleGroup.Chest))
            assertEquals(SpecializationLevel.Maintenance, emitted.levelFor(MuscleGroup.Back))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given Maintenance levels when saved then they are not persisted")
    fun maintenanceLevelsNotPersisted() = runTest {
        subject.save(
            SpecializationPreference(
                levels = mapOf(
                    MuscleGroup.Chest to SpecializationLevel.Specialize,
                    MuscleGroup.Back to SpecializationLevel.Maintenance,
                ),
            ),
        )

        subject.observe().test(timeout = roomFirstEmissionTimeout) {
            val emitted = awaitItem()
            assertEquals(SpecializationLevel.Specialize, emitted.levelFor(MuscleGroup.Chest))
            assertEquals(SpecializationLevel.Maintenance, emitted.levelFor(MuscleGroup.Back))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
