package com.stratum.core.data.sync.handlers

import androidx.room.Room
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.appliers.LiftRemoteApplier
import com.stratum.core.data.sync.appliers.LoggedSetRemoteApplier
import com.stratum.core.data.sync.appliers.ProgramRemoteApplier
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.core.data.sync.testSyncTouch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SyncHandlerRegistryTest {
    @Test
    fun `registry covers every SyncCollection exactly once in hydrate order`() {
        val db =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        val encoders = SyncEntityEncoders()
        val decoders = SyncEntityDecoders()
        val provider =
            SyncHandlersProvider(
                database = db,
                syncTouch = testSyncTouch(RuntimeEnvironment.getApplication(), db.pendingCloudDeleteDao()),
                userProfileDao = db.userProfileDao(),
                onboardingDao = db.onboardingProgressDao(),
                gymDao = db.gymPreferenceDao(),
                specializationDao = db.specializationPreferenceDao(),
                equipmentDao = db.equipmentDao(),
                liftDao = db.liftDao(),
                incrementTierDao = db.incrementTierDao(),
                programDao = db.programDao(),
                workoutDao = db.workoutDao(),
                workoutLiftDao = db.workoutLiftDao(),
                liftSetDao = db.liftSetDao(),
                sessionDao = db.trainingSessionDao(),
                liftSessionNoteDao = db.liftSessionNoteDao(),
                liftNoteDao = db.liftNoteDao(),
                workoutLiftNoteDao = db.workoutLiftNoteDao(),
                loggedSetDao = db.trainingLoggedSetDao(),
                programChangeEventDao = db.programChangeEventDao(),
                encoders = encoders,
                decoders = decoders,
                liftRemoteApplier = LiftRemoteApplier(db, db.liftDao(), encoders, decoders),
                programRemoteApplier = ProgramRemoteApplier(db.programDao(), decoders),
                loggedSetRemoteApplier = LoggedSetRemoteApplier(db.trainingLoggedSetDao(), decoders),
            )
        val registry = SyncHandlerRegistry(provider)
        val ordered = registry.inHydrateOrder()
        assertEquals(SyncCollection.entries.size, ordered.size)
        assertEquals(SyncCollection.HYDRATE_ORDER, ordered.map { it.collection })
        assertTrue(ordered.map { it.collection }.toSet() == SyncCollection.entries.toSet())
        fun index(c: SyncCollection) = ordered.indexOfFirst { it.collection == c }
        assertTrue(index(SyncCollection.Equipment) < index(SyncCollection.Lifts))
        assertTrue(index(SyncCollection.Lifts) < index(SyncCollection.IncrementTiers))
        assertTrue(index(SyncCollection.Lifts) < index(SyncCollection.LiftCatalogNotes))
        assertTrue(index(SyncCollection.Programs) < index(SyncCollection.Workouts))
        assertTrue(index(SyncCollection.WorkoutLifts) < index(SyncCollection.WorkoutLiftNotes))
        assertTrue(index(SyncCollection.TrainingSessions) < index(SyncCollection.LoggedSets))
        db.close()
    }
}
