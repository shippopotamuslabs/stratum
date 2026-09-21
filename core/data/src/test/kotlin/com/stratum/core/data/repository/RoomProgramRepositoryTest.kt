package com.stratum.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.SyncCollection
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * [RoomProgramRepository] tests. Scenarios PROG-GEN-200, 201, 202, 203. Run with Robolectric
 * so a real in-memory [StratumDatabase] backs the DAOs.
 */
@ExtendWith(RobolectricExtension::class)
class RoomProgramRepositoryTest {
    private lateinit var database: StratumDatabase
    private lateinit var repository: RoomProgramRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomProgramRepository(
            database = database,
            programDao = database.programDao(),
            workoutDao = database.workoutDao(),
            workoutLiftDao = database.workoutLiftDao(),
            liftSetDao = database.liftSetDao(),
            trainingSessionDao = database.trainingSessionDao(),
            programChangeEventDao = database.programChangeEventDao(),
            syncTouch = com.stratum.core.data.sync.testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Nested
    @DisplayName("PROG-GEN-200 empty repo returns null")
    inner class EmptyReads {
        @Test
        fun `getProgram returns null when empty`() = runTest {
            assertNull(repository.getProgram())
        }

        @Test
        fun `observeProgram first emission is null when empty`() = runTest {
            repository.observeProgram().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("observeProgram reacts to workout_lift re-point without program row rewrite")
    inner class ObserveRepoint {
        @Test
        @DisplayName("Given active program when workout_lifts re-pointed then observeProgram re-emits survivor id")
        fun repointLiftIdsTriggersObserveProgram() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)

            repository.observeProgram().test {
                val initial = awaitItem()
                assertEquals(LiftId("bench-press"), initial!!.workouts[0].workoutLifts[0].liftId)

                // Merge path writes only workout_lifts — program entity is untouched.
                database.workoutLiftDao().repointLiftIds(
                    sourceLiftIds = listOf(LiftId("bench-press")),
                    targetLiftId = LiftId("back-squat"),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )

                val afterRepoint = awaitItem()
                assertEquals(LiftId("back-squat"), afterRepoint!!.workouts[0].workoutLifts[0].liftId)
                assertEquals(WorkoutLiftId("wl-1"), afterRepoint.workouts[0].workoutLifts[0].id)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("PROG-GEN-201 full hierarchy round-trips")
    inner class RoundTrip {
        @Test
        fun `saveProgram then getProgram returns an equal-by-value Program`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)

            val loaded = repository.getProgram()

            assertEquals(PROGRAM_P1, loaded)
            assertEquals(1, loaded!!.cycleLength)
            assertEquals(1, loaded!!.workouts.size)
            assertEquals(1, loaded.workouts[0].workoutLifts.size)
            assertEquals(2, loaded.workouts[0].workoutLifts[0].sets.size)
            assertEquals(SetType.Standard, loaded.workouts[0].workoutLifts[0].sets[0].setType)
            assertEquals(Weekday.Monday, loaded.workouts[0].weekday)
            assertEquals(ProgressionScheme.Linear, loaded.workouts[0].workoutLifts[0].progressionScheme)
        }

        @Test
        fun `rest seconds and superset fields round-trip`() = runTest {
            seedReferencedLifts()
            val programWithSuperset =
                PROGRAM_P1.copy(
                    workouts =
                        PROGRAM_P1.workouts.map { workout ->
                            workout.copy(
                                workoutLifts =
                                    listOf(
                                        workout.workoutLifts[0].copy(
                                            supersetGroupId = WorkoutSupersetGroupId("sg-1"),
                                            useRestTimerBetweenLifts = true,
                                            sets =
                                                workout.workoutLifts[0].sets.mapIndexed { index, set ->
                                                    set.copy(restSeconds = 90 + index * 30)
                                                },
                                        ),
                                    ),
                            )
                        },
                )
            repository.saveProgram(programWithSuperset)

            val loaded = repository.getProgram()!!.workouts[0].workoutLifts[0]

            assertEquals(WorkoutSupersetGroupId("sg-1"), loaded.supersetGroupId)
            assertTrue(loaded.useRestTimerBetweenLifts)
            assertEquals(90, loaded.sets[0].restSeconds)
            assertEquals(120, loaded.sets[1].restSeconds)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-202 unbound weekday persists as null")
    inner class NullWeekday {
        @Test
        fun `a null weekday round-trips as null`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(workouts = PROGRAM_P1.workouts.map { it.copy(weekday = null) }))

            assertEquals(null, repository.getProgram()!!.workouts[0].weekday)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-203 multi-program: save demotes prior active; same-id replace scoped children")
    inner class MultiProgram {
        @Test
        fun `saving a second active program keeps the first as library inventory`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            repository.saveProgram(PROGRAM_P2.copy(isActive = true))

            val active = repository.getProgram()
            assertEquals(PROGRAM_P2.copy(isActive = true), active)
            assertEquals(2, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM programs").use { c ->
                c.moveToFirst(); c.getInt(0)
            })
            val library = repository.getProgramById(PROGRAM_P1.id)
            assertEquals(PROGRAM_P1.copy(isActive = false), library)
        }

        @Test
        fun `re-saving the same program id replaces only that program children`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            val slim =
                PROGRAM_P1.copy(
                    isActive = true,
                    workouts =
                        PROGRAM_P1.workouts.map { workout ->
                            workout.copy(
                                workoutLifts =
                                    workout.workoutLifts.map { lift ->
                                        lift.copy(sets = lift.sets.take(1))
                                    },
                            )
                        },
                )
            repository.saveProgram(slim)

            val loaded = repository.getProgram()
            assertEquals(slim, loaded)
            assertEquals(1, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sets").use { c ->
                c.moveToFirst(); c.getInt(0)
            })
        }

        @Test
        fun `saveToLibrary does not change the active program`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            repository.saveToLibrary(PROGRAM_P2)

            assertEquals(PROGRAM_P1.copy(isActive = true), repository.getProgram())
            assertEquals(PROGRAM_P2.copy(isActive = false), repository.getProgramById(PROGRAM_P2.id))
        }

        @Test
        fun `deleteProgram keeps sessions and nulls soft refs`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            database.trainingSessionDao().upsert(
                com.stratum.core.data.db.TrainingSessionEntity(
                    id = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                    programId = PROGRAM_P1.id,
                    workoutId = WorkoutId("w-1"),
                    status = com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
                    kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = com.stratum.domain.training.TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = Weekday.Monday,
                    startedAtEpochMillis = Instant.parse("2026-06-26T12:00:00Z").toEpochMilli(),
                    endedAtEpochMillis = Instant.parse("2026-06-26T13:00:00Z").toEpochMilli(),
                    sourceApp = null,
                    programNameSnapshot = "Push/Pull/Legs",
                    workoutNameSnapshot = "Push Day",
                ),
            )
            database.trainingLoggedSetDao().upsert(
                com.stratum.core.data.db.LoggedSetEntity(
                    id = com.stratum.domain.importing.identity.LoggedSetId("set-1"),
                    sessionId = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                    exerciseNameRaw = "Bench",
                    liftId = LiftId("bench-press"),
                    setIndex = 0,
                    weightAmount = 135.0,
                    weightUnit = "Lb",
                    reps = 8,
                    rir = 2,
                    completedAtEpochMillis = Instant.parse("2026-06-26T12:30:00Z").toEpochMilli(),
                    liftName = "Barbell Bench Press",
                ),
            )

            repository.deleteProgram(PROGRAM_P1.id)

            assertNull(repository.getProgramById(PROGRAM_P1.id))
            assertNull(repository.getProgram())
            val session =
                database.trainingSessionDao().getById(
                    com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                )
            assertNull(session?.programId)
            assertNull(session?.workoutId)
            assertEquals("Push/Pull/Legs", session?.programNameSnapshot)
            assertEquals("Push Day", session?.workoutNameSnapshot)
            val sets =
                database.trainingLoggedSetDao().getForSession(
                    com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                )
            assertEquals(1, sets.size)
        }

        @Test
        fun `deleteProgram enqueues program_change_events for cloud tombstone`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            database.programChangeEventDao().upsert(
                com.stratum.core.data.db.ProgramChangeEventEntity(
                    id = com.stratum.domain.training.identity.ProgramChangeEventId("pce-1"),
                    programId = PROGRAM_P1.id,
                    eventType = com.stratum.domain.training.ProgramChangeEventType.CYCLE_COMPLETED,
                    occurredAtEpochMillis = Instant.parse("2026-06-26T12:00:00Z").toEpochMilli(),
                    completedCycleNumber = 1,
                ),
            )

            repository.deleteProgram(PROGRAM_P1.id)

            val pending = database.pendingCloudDeleteDao().peekPending(50)
            val eventDeletes =
                pending.filter {
                    it.collectionPath == com.stratum.core.data.sync.SyncCollection.ProgramChangeEvents.firestorePath
                }
            assertTrue(eventDeletes.any { it.entityId == "pce-1" })
            assertTrue(
                pending.any {
                    it.collectionPath == com.stratum.core.data.sync.SyncCollection.Programs.firestorePath &&
                        it.entityId == PROGRAM_P1.id.value
                },
            )
        }

        @Test
        fun `saveProgram rewrite preserves session workout soft refs`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1.copy(isActive = true))
            database.trainingSessionDao().upsert(
                com.stratum.core.data.db.TrainingSessionEntity(
                    id = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                    programId = PROGRAM_P1.id,
                    workoutId = WorkoutId("w-1"),
                    status = com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
                    kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = com.stratum.domain.training.TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = Weekday.Monday,
                    startedAtEpochMillis = Instant.parse("2026-06-26T12:00:00Z").toEpochMilli(),
                    endedAtEpochMillis = Instant.parse("2026-06-26T13:00:00Z").toEpochMilli(),
                    sourceApp = null,
                    programNameSnapshot = "Push/Pull/Legs",
                    workoutNameSnapshot = "Push Day",
                ),
            )

            // Full hierarchy rewrite (same workout id) must not clear soft refs.
            repository.saveProgram(
                PROGRAM_P1.copy(
                    isActive = true,
                    name = "Push/Pull/Legs Renamed",
                ),
            )

            val session =
                database.trainingSessionDao().getById(
                    com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                )
            assertEquals(PROGRAM_P1.id, session?.programId)
            assertEquals(WorkoutId("w-1"), session?.workoutId)
        }
    }

    @Nested
    @DisplayName("workout note lists")
    inner class WorkoutNoteLists {
        @Test
        fun `set and get round-trips an ordered list with a legacy mirror`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")

            assertTrue(repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "a"), WorkoutLiftNoteInput(body = "b"))))

            val stored = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(listOf("a", "b"), stored.map { it.body })
            assertEquals(listOf(0, 1), stored.map { it.position })
            assertEquals("a", database.workoutLiftDao().getById(slot)?.pinnedNote)
        }

        @Test
        fun `missing slot returns false and empty`() = runTest {
            assertFalse(repository.setWorkoutLiftPinnedNotes(WorkoutLiftId("ghost"), listOf(WorkoutLiftNoteInput(body = "x"))))
            assertTrue(repository.getWorkoutLiftPinnedNotes(WorkoutLiftId("ghost")).isEmpty())
        }

        @Test
        fun `unchanged hierarchy re-save preserves note ids`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "kept")))
            val id = repository.getWorkoutLiftPinnedNotes(slot).single().id

            // Re-save the stored program unchanged: the pin was not edited, so the list stays.
            val storedProgram = repository.getProgramById(ProgramId("p-1"))!!
            repository.saveProgram(storedProgram)

            val stored = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(listOf("kept"), stored.map { it.body })
            assertEquals(id, stored.single().id)
        }

        @Test
        fun `changed scalar pin drops the stale list`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "old pin")))

            val changed = PROGRAM_P1.copy(
                workouts = PROGRAM_P1.workouts.map { workout ->
                    workout.copy(
                        workoutLifts = workout.workoutLifts.map { lift ->
                            if (lift.id == slot) lift.copy(pinnedNote = "new pin") else lift
                        },
                    )
                },
            )
            repository.saveProgram(changed)

            assertEquals(listOf("new pin"), repository.getWorkoutLiftPinnedNotes(slot).map { it.body })
        }

        @Test
        fun `saveProgram with a carried note list persists rows in order with a first-note mirror`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "old")))
            val oldId = repository.getWorkoutLiftPinnedNotes(slot).single().id

            val carried = PROGRAM_P1.copy(
                workouts = PROGRAM_P1.workouts.map { workout ->
                    workout.copy(
                        workoutLifts = workout.workoutLifts.map { lift ->
                            if (lift.id == slot) {
                                lift.copy(
                                    pinnedNote = "alpha",
                                    pinnedNotes = listOf("alpha", "beta"),
                                )
                            } else {
                                lift
                            }
                        },
                    )
                },
            )
            repository.saveProgram(carried)

            val stored = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(listOf("alpha", "beta"), stored.map { it.body })
            assertEquals(listOf(0, 1), stored.map { it.position })
            assertEquals("alpha", database.workoutLiftDao().getById(slot)?.pinnedNote)
            val tombstoned =
                database.pendingCloudDeleteDao().peekPending()
                    .filter { it.collectionPath == SyncCollection.WorkoutLiftNotes.firestorePath }
                    .map { it.entityId }
                    .toSet()
            assertTrue(oldId.value in tombstoned)
        }

        @Test
        fun `reading a legacy-only pin migrates it to a position-0 row`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            // Simulate pre-backfill storage: legacy column set, no child rows.
            database.workoutLiftDao().setPinnedNote(slot, "legacy", 1L)

            val first = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(listOf("legacy"), first.map { it.body })
            assertEquals(0, first.single().position)

            // Second read hits the migrated row — stable id, no duplicate rows.
            val second = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(first.single().id, second.single().id)
            assertEquals("legacy", database.workoutLiftDao().getById(slot)?.pinnedNote)
        }

        @Test
        fun `deleteProgram cascades slot notes`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "pin")))

            repository.deleteProgram(ProgramId("p-1"))

            assertTrue(repository.getWorkoutLiftPinnedNotes(slot).isEmpty())
        }

        @Test
        fun `scalar edit updates the table row, not just the mirror`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            database.workoutLiftDao().setPinnedNote(slot, "legacy", 1L)
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "migrated")))
            val rowId = repository.getWorkoutLiftPinnedNotes(slot).single().id

            assertTrue(repository.setWorkoutLiftPinnedNote(slot, "edited"))

            val stored = repository.getWorkoutLiftPinnedNotes(slot)
            assertEquals(listOf("edited"), stored.map { it.body })
            assertEquals(rowId, stored.single().id)
            assertEquals("edited", database.workoutLiftDao().getById(slot)?.pinnedNote)
        }

        @Test
        fun `scalar clear removes rows and mirror`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(
                slot,
                listOf(WorkoutLiftNoteInput(body = "one"), WorkoutLiftNoteInput(body = "two")),
            )

            assertTrue(repository.setWorkoutLiftPinnedNote(slot, null))

            assertTrue(repository.getWorkoutLiftPinnedNotes(slot).isEmpty())
            assertEquals(null, database.workoutLiftDao().getById(slot)?.pinnedNote)
        }

        @Test
        fun `removed rows are tombstoned on replace and program delete`() = runTest {
            seedReferencedLifts()
            repository.saveProgram(PROGRAM_P1)
            val slot = WorkoutLiftId("wl-1")
            repository.setWorkoutLiftPinnedNotes(
                slot,
                listOf(WorkoutLiftNoteInput(body = "keep"), WorkoutLiftNoteInput(body = "drop")),
            )
            val droppedId = repository.getWorkoutLiftPinnedNotes(slot).last().id
            repository.setWorkoutLiftPinnedNotes(slot, listOf(WorkoutLiftNoteInput(body = "keep")))

            val keptId = repository.getWorkoutLiftPinnedNotes(slot).single().id
            repository.deleteProgram(ProgramId("p-1"))

            val tombstoned =
                database.pendingCloudDeleteDao().peekPending()
                    .filter { it.collectionPath == SyncCollection.WorkoutLiftNotes.firestorePath }
                    .map { it.entityId }
                    .toSet()
            assertTrue(droppedId.value in tombstoned)
            assertTrue(keptId.value in tombstoned)
        }
    }

    private suspend fun seedReferencedLifts() {
        database.liftDao().upsertAll(
            listOf(
                minimalLift(LiftId("bench-press"), MovementPattern.HorizontalPress),
                minimalLift(LiftId("back-squat"), MovementPattern.SquatPattern),
            ),
        )
    }

    private fun minimalLift(id: LiftId, pattern: MovementPattern): LiftEntity = LiftEntity(
        id = id,
        name = id.value,
        movementPatternsBitmask = 1 shl pattern.ordinal,
        primaryMusclesBitmask = 0,
        secondaryMusclesBitmask = 0,
        presetTags = ",",
        isCustom = false,
        enabled = true,
    )

    private companion object {
        val PROGRAM_P1: Program = Program(
            id = ProgramId("p-1"),
            name = "Push/Pull/Legs",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            isActive = true,
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    LiftSetId("ls-1"), WorkoutLiftId("wl-1"), 0,
                                    SetType.Standard, 8, 12, 2, isTopSet = true,
                                ),
                                LiftSet(
                                    LiftSetId("ls-2"), WorkoutLiftId("wl-1"), 1,
                                    SetType.Standard, 8, 12, 2, isTopSet = false,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val PROGRAM_P2: Program = Program(
            id = ProgramId("p-2"),
            name = "Minimal",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Tuesday),
            isActive = true,
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-2"),
                    programId = ProgramId("p-2"),
                    name = "Only Day",
                    weekday = Weekday.Tuesday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-2"),
                            workoutId = WorkoutId("w-2"),
                            liftId = LiftId("back-squat"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    LiftSetId("ls-3"), WorkoutLiftId("wl-2"), 0,
                                    SetType.Standard, 5, 5, 2, isTopSet = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
