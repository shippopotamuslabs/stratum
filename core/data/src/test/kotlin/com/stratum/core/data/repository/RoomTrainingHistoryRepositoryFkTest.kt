package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toEntity
import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.TrainingSessionOrigin
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class RoomTrainingHistoryRepositoryFkTest {
    @Test
    @DisplayName("replaceAll persists import sessions in training_sessions before logged_sets FK insert")
    fun replaceAllSatisfiesLoggedSetsForeignKey() {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = RoomTrainingHistoryRepository(
            database = database,
            sessionDao = database.trainingSessionDao(),
            setDao = database.trainingLoggedSetDao(),
            programDao = database.programDao(),
        )
        val sessionId = HistoricalSessionId("import-session-1")
        val sessions =
            listOf(
                HistoricalSession(
                    id = sessionId,
                    startedAt = Instant.parse("2025-01-01T10:00:00Z"),
                    endedAt = Instant.parse("2025-01-01T11:00:00Z"),
                    sourceApp = "test",
                ),
            )
        val sets =
            listOf(
                LoggedSet(
                    id = LoggedSetId("set-1"),
                    sessionId = sessionId,
                    exerciseNameRaw = "Bench Press",
                    liftId = null,
                    setIndex = 0,
                    weight = Weight(135.0, Weight.WeightUnit.Lb),
                    reps = 5,
                    rir = 2,
                    completedAt = Instant.parse("2025-01-01T10:30:00Z"),
                ),
            )

        runBlocking { repository.replaceAll(sessions, sets) }

        val storedSession = runBlocking {
            database.trainingSessionDao().getById(TrainingSessionId(sessionId.value))
        }
        assertNotNull(storedSession)
        assertEquals(TrainingSessionOrigin.IMPORT, storedSession!!.origin)
        assertEquals(1, runBlocking { database.trainingLoggedSetDao().getForSession(storedSession.id).size })

        database.close()
    }
}
