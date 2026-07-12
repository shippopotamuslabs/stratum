package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnalyzeSessionEquipmentProposalsUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val sessionId = TrainingSessionId("ts-cable-1")
    private val latId = LiftId("lat-pulldown")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private lateinit var logRepo: FakeTrainingLogRepository
    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var equipmentRepo: FakeEquipmentRepository
    private lateinit var prefs: FakeEquipmentProposalPreferences
    private lateinit var subject: AnalyzeSessionEquipmentProposalsUseCase

    @BeforeEach
    fun setup() {
        logRepo = FakeTrainingLogRepository()
        liftRepo = FakeLiftRepository()
        equipmentRepo = FakeEquipmentRepository()
        prefs = FakeEquipmentProposalPreferences()
        equipmentRepo.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = openFive,
                    isCustom = false,
                ),
            ),
        )
        subject =
            AnalyzeSessionEquipmentProposalsUseCase(
                trainingLogRepository = logRepo,
                liftRepository = liftRepo,
                equipmentRepository = equipmentRepo,
                preferences = prefs,
                loggingPort = FakeLoggingPort(),
            )
    }

    @Nested
    @DisplayName("session analysis")
    inner class Analysis {
        @Test
        @DisplayName(
            "Given 62.5 on cable seed with many inheritors when analyzed then Fork primary",
        )
        fun cableOffLatticeForksWithManyInheritors() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 2, id = "s3"))

            val proposals = subject(sessionId)

            assertEquals(1, proposals.size)
            val proposal = proposals.single()
            assertTrue(proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment)
            assertEquals(cableId, proposal.clusterEquipmentId)
            assertEquals(listOf(latId), proposal.liftIds)
            assertTrue(proposal.affectedInheritingLiftCount > EQUIPMENT_INCREMENT_FORK_THRESHOLD_K)
            assertTrue(proposal.sampleWeights.any { it.amount == 62.5 })
        }

        @Test
        @DisplayName("Given dismissed fingerprint when analyzed then proposal is filtered out")
        fun dismissedFingerprintFiltered() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 1, id = "s2"))

            val beforeDismiss = subject(sessionId)
            assertEquals(1, beforeDismiss.size)
            prefs.dismiss(beforeDismiss.single().id)

            val afterDismiss = subject(sessionId)
            assertTrue(afterDismiss.isEmpty())
        }

        @Test
        @DisplayName("Given feature disabled when analyzed then no proposals")
        fun featureDisabledNoProposals() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 1, id = "s2"))
            prefs.seedEnabled(false)

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName("Given on-lattice weights when analyzed then no proposals")
        fun onLatticeNoProposal() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 4)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 60.0, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 65.0, setIndex = 1, id = "s2"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName("Given typo weight 625 with peers around 60 when analyzed then typo dropped")
        fun typoWeightIgnored() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            // On-lattice peers + one typo residual
            logRepo.seedSet(setAt(latId, 60.0, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 65.0, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 625.0, setIndex = 2, id = "s3"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName(
            "Given single residual set on high-inheritor seed when analyzed then suppressed",
        )
        fun singleResidualOnHighInheritorSeedSuppressed() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            // Typo-shaped: one odd weight among on-lattice peers (33.3 / 35 / 35).
            logRepo.seedSet(setAt(latId, 35.0, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 35.0, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 33.3, setIndex = 2, id = "s3"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName(
            "Given only one residual set in a one-set workout on seed when analyzed then suppressed",
        )
        fun singleSetWorkoutOnSeedSuppressed() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 33.33, setIndex = 0, id = "s1"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName(
            "Given repeated residual sets on high-inheritor seed when analyzed then proposes",
        )
        fun repeatedResidualOnSeedProposes() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))

            val proposals = subject(sessionId)
            assertEquals(1, proposals.size)
            assertTrue(proposals.single().primary is EquipmentIncrementRemedy.ForkCustomEquipment)
        }

        @Test
        @DisplayName("Given bodyweight lift when analyzed then skipped")
        fun bodyweightSkipped() = runTest {
            val bwId = LiftId("pull-up")
            liftRepo.seedLifts(
                listOf(
                    Lift(
                        id = bwId,
                        name = "Pull-up",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers = emptyList(),
                        presetTags = emptySet(),
                        equipmentId = EquipmentId("bodyweight"),
                        usesEquipmentDefaultIncrements = true,
                        enabled = true,
                        loadType = LoadType.Bodyweight,
                    ),
                ),
            )
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(bwId, 0.0, setIndex = 0, id = "s1"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName("Given import session with residuals when analyzed then empty")
        fun importSessionEmitsNothing() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.importTrainingSession(id = sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 2, id = "s3"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName("Given missing session when analyzed then empty")
        fun missingSessionEmitsNothing() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            // Sets alone are not enough — session gate requires a completed live row.
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 1, id = "s2"))

            assertTrue(subject(sessionId).isEmpty())
        }

        @Test
        @DisplayName("Given completed live session when analyzed then proposals emitted")
        fun completedLiveEmitsProposals() = runTest {
            seedCableCatalogWithInheritors(inheritorCount = 12)
            val session = WorkoutLoggingFixtures.completedLiveSession(sessionId.value)
            assertEquals(TrainingSessionStatus.COMPLETED, session.status)
            assertEquals(TrainingSessionOrigin.LIVE, session.origin)
            logRepo.seedSession(session)
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 2, id = "s3"))

            assertEquals(1, subject(sessionId).size)
        }

        @Test
        @DisplayName(
            "Given single anomalous lift with override tiers when analyzed then LiftOverride primary",
        )
        fun allOverrideLiftsPreferLiftOverride() = runTest {
            val overrideTiers =
                listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
            liftRepo.seedLifts(
                listOf(
                    Lift(
                        id = latId,
                        name = "Lat pulldown",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers = overrideTiers,
                        presetTags = emptySet(),
                        equipmentId = cableId,
                        equipmentName = "Cables",
                        usesEquipmentDefaultIncrements = false,
                        enabled = true,
                    ),
                    // Other inheritor keeps seed blast radius > 0 without affecting signal.
                    Lift(
                        id = LiftId("other-cable"),
                        name = "Other cable",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers = openFive,
                        presetTags = emptySet(),
                        equipmentId = cableId,
                        equipmentName = "Cables",
                        usesEquipmentDefaultIncrements = true,
                        enabled = true,
                    ),
                ),
            )
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 1, id = "s2"))

            val proposals = subject(sessionId)
            assertEquals(1, proposals.size)
            assertTrue(proposals.single().primary is EquipmentIncrementRemedy.LiftOverride)
            assertFalse(
                proposals.single().primary is EquipmentIncrementRemedy.AmendEquipmentTiers,
            )
        }

        @Test
        @DisplayName(
            "Given fork name colliding with existing equipment when analyzed then unique suffix",
        )
        fun forkNameUniqueifiedOnCollision() = runTest {
            equipmentRepo.seed(
                listOf(
                    Equipment(
                        id = cableId,
                        name = "Cables",
                        incrementTiers = openFive,
                        isCustom = false,
                    ),
                    Equipment(
                        id = EquipmentId("existing-stack"),
                        name = "Lat pulldown stack",
                        incrementTiers = openFive,
                        isCustom = true,
                    ),
                ),
            )
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 2, id = "s3"))

            val proposals = subject(sessionId)
            assertEquals(1, proposals.size)
            val fork = proposals.single().primary as EquipmentIncrementRemedy.ForkCustomEquipment
            assertEquals("Lat pulldown stack (2)", fork.suggestedName)
        }

        @Test
        @DisplayName(
            "Given empty equipment tiers when analyzed then inference uses effective lift tiers",
        )
        fun emptyEquipmentTiersFallsBackToEffective() = runTest {
            equipmentRepo.seed(
                listOf(
                    Equipment(
                        id = cableId,
                        name = "Cables",
                        incrementTiers = emptyList(),
                        isCustom = false,
                    ),
                ),
            )
            seedCableCatalogWithInheritors(inheritorCount = 12)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "s1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "s2"))
            logRepo.seedSet(setAt(latId, 67.5, setIndex = 2, id = "s3"))

            val proposals = subject(sessionId)
            assertEquals(1, proposals.size)
            assertTrue(proposals.single().liftNames.contains("Lat pulldown"))
            assertEquals("Cables", proposals.single().equipmentName)
        }

        @Test
        @DisplayName(
            "Given two clusters when analyzed then stronger residual signal is ordered first",
        )
        fun ranksClustersBySignalNotInsertionOrder() = runTest {
            val customId = EquipmentId("custom-stack")
            val customLiftId = LiftId("custom-lift")
            equipmentRepo.seed(
                listOf(
                    Equipment(
                        id = cableId,
                        name = "Cables",
                        incrementTiers = openFive,
                        isCustom = false,
                    ),
                    Equipment(
                        id = customId,
                        name = "Custom stack",
                        incrementTiers = openFive,
                        isCustom = true,
                    ),
                ),
            )
            // Seed weak cluster first in set list (1 residual set on high inheritor seed is
            // suppressed — use 2 residual sets). Strong custom cluster gets 4 residual sets.
            val lifts =
                buildList {
                    add(
                        Lift(
                            id = latId,
                            name = "Lat pulldown",
                            movementPatterns = setOf(MovementPattern.VerticalPull),
                            primaryMuscles = setOf(MuscleGroup.Back),
                            secondaryMuscles = emptySet(),
                            incrementTiers = openFive,
                            presetTags = emptySet(),
                            equipmentId = cableId,
                            equipmentName = "Cables",
                            usesEquipmentDefaultIncrements = true,
                            enabled = true,
                        ),
                    )
                    repeat(11) { i ->
                        add(
                            Lift(
                                id = LiftId("cable-lift-$i"),
                                name = "Cable lift $i",
                                movementPatterns = setOf(MovementPattern.VerticalPull),
                                primaryMuscles = setOf(MuscleGroup.Back),
                                secondaryMuscles = emptySet(),
                                incrementTiers = openFive,
                                presetTags = emptySet(),
                                equipmentId = cableId,
                                equipmentName = "Cables",
                                usesEquipmentDefaultIncrements = true,
                                enabled = true,
                            ),
                        )
                    }
                    add(
                        Lift(
                            id = customLiftId,
                            name = "Custom machine press",
                            movementPatterns = setOf(MovementPattern.HorizontalPress),
                            primaryMuscles = setOf(MuscleGroup.Chest),
                            secondaryMuscles = emptySet(),
                            incrementTiers = openFive,
                            presetTags = emptySet(),
                            equipmentId = customId,
                            equipmentName = "Custom stack",
                            usesEquipmentDefaultIncrements = true,
                            enabled = true,
                        ),
                    )
                }
            liftRepo.seedLifts(lifts)
            logRepo.seedSession(WorkoutLoggingFixtures.completedLiveSession(sessionId.value))
            // Weak cable: 2 residual sets
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 0, id = "c1"))
            logRepo.seedSet(setAt(latId, 62.5, setIndex = 1, id = "c2"))
            // Strong custom: 4 residual sets
            logRepo.seedSet(setAt(customLiftId, 62.5, setIndex = 0, id = "m1"))
            logRepo.seedSet(setAt(customLiftId, 62.5, setIndex = 1, id = "m2"))
            logRepo.seedSet(setAt(customLiftId, 67.5, setIndex = 2, id = "m3"))
            logRepo.seedSet(setAt(customLiftId, 67.5, setIndex = 3, id = "m4"))

            val proposals = subject(sessionId)
            assertEquals(2, proposals.size)
            assertEquals(customId, proposals.first().clusterEquipmentId)
            assertTrue(proposals.first().residualSetCount >= proposals.last().residualSetCount)
        }
    }

    @Nested
    @DisplayName("filterTypoWeights")
    inner class TypoFilter {
        @Test
        @DisplayName("Given 625 with peers near 60 when filtered then 625 removed")
        fun removesTypo() {
            val filtered =
                filterTypoWeights(
                    listOf(
                        Weight(60.0, lb),
                        Weight(65.0, lb),
                        Weight(625.0, lb),
                    ),
                )
            assertEquals(2, filtered.size)
            assertTrue(filtered.none { it.amount == 625.0 })
        }

        @Test
        @DisplayName("Given 625 among near-zero peers when filtered then 625 removed")
        fun removesLoneSpikeAmongZeros() {
            val filtered =
                filterTypoWeights(
                    listOf(
                        Weight(0.0, lb),
                        Weight(0.0, lb),
                        Weight(625.0, lb),
                    ),
                )
            assertTrue(filtered.none { it.amount == 625.0 })
        }
    }

    private fun seedCableCatalogWithInheritors(inheritorCount: Int) {
        val lifts =
            buildList {
                add(
                    Lift(
                        id = latId,
                        name = "Lat pulldown",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers = openFive,
                        presetTags = emptySet(),
                        equipmentId = cableId,
                        equipmentName = "Cables",
                        usesEquipmentDefaultIncrements = true,
                        enabled = true,
                    ),
                )
                repeat(inheritorCount - 1) { i ->
                    add(
                        Lift(
                            id = LiftId("cable-lift-$i"),
                            name = "Cable lift $i",
                            movementPatterns = setOf(MovementPattern.VerticalPull),
                            primaryMuscles = setOf(MuscleGroup.Back),
                            secondaryMuscles = emptySet(),
                            incrementTiers = openFive,
                            presetTags = emptySet(),
                            equipmentId = cableId,
                            equipmentName = "Cables",
                            usesEquipmentDefaultIncrements = true,
                            enabled = true,
                        ),
                    )
                }
            }
        liftRepo.seedLifts(lifts)
    }

    private fun setAt(
        liftId: LiftId,
        amount: Double,
        setIndex: Int,
        id: String,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Lat pulldown",
            liftId = liftId,
            setIndex = setIndex,
            weight = Weight(amount, lb),
            reps = 10,
            rir = 2,
            completedAt = Instant.parse("2025-01-01T10:30:00Z"),
            liftContext = WorkoutLoggingFixtures.importLiftContext("Lat pulldown"),
        )
}
