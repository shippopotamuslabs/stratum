package com.stratum.domain.response.fixtures

import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.WeeklyTrainingSignal
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object ResponseFixtures {
    val liftBench = LiftId("lift-bench")
    val liftFly = LiftId("lift-fly")
    val liftIncline = LiftId("lift-incline")
    val liftSquat = LiftId("lift-squat")
    val liftPull = LiftId("lift-pull")
    val liftVerticalPull = LiftId("lift-lat-pulldown")

    private val baseInstant = Instant.parse("2025-01-06T10:00:00Z")

    fun weekStart(year: Int, month: Int, day: Int): Instant =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC)

    fun benchPressLift(): Lift = lift(
        id = liftBench,
        name = "Barbell Bench Press",
        pattern = MovementPattern.HorizontalPress,
        muscle = MuscleGroup.Chest,
    )

    fun dumbbellFlyLift(): Lift = lift(
        id = liftFly,
        name = "Dumbbell Fly",
        pattern = MovementPattern.ShoulderAdduction,
        muscle = MuscleGroup.Chest,
    )

    fun inclineBenchLift(): Lift = lift(
        id = liftIncline,
        name = "Incline Bench Press",
        pattern = MovementPattern.InclinePress,
        muscle = MuscleGroup.Chest,
    )

    fun squatLift(): Lift = lift(
        id = liftSquat,
        name = "Back Squat",
        pattern = MovementPattern.SquatPattern,
        muscle = MuscleGroup.Quad,
    )

    /** FIX-S1-WORKING — single session, bench press 100 kg × 5 @ RIR 2 */
    fun fixS1Working(): ResponseExtractionInput {
        val session = session("s1-working", baseInstant)
        val sets =
            listOf(
                loggedSet(
                    id = "set-working",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = baseInstant.plusSeconds(60),
                ),
            )
        return input(sessions = listOf(session), sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S1-WARMUP-RIR — top set plus prior warmup 60 kg × 10 @ RIR 6 */
    fun fixS1WarmupRir(): ResponseExtractionInput {
        val session = session("s1-warmup-rir", baseInstant)
        val sets =
            listOf(
                loggedSet(
                    id = "set-warmup",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 60.0,
                    reps = 10,
                    rir = 6,
                    completedAt = baseInstant.plusSeconds(30),
                ),
                loggedSet(
                    id = "set-top",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = baseInstant.plusSeconds(120),
                ),
            )
        return input(sessions = listOf(session), sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S1-WARMUP-LOAD — top 100 kg plus warmup at 60% load, RIR null on warmup */
    fun fixS1WarmupLoad(): ResponseExtractionInput {
        val session = session("s1-warmup-load", baseInstant)
        val sets =
            listOf(
                loggedSet(
                    id = "set-warmup-load",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 60.0,
                    reps = 10,
                    rir = null,
                    completedAt = baseInstant.plusSeconds(30),
                ),
                loggedSet(
                    id = "set-top-load",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = baseInstant.plusSeconds(120),
                ),
            )
        return input(sessions = listOf(session), sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S1-MIXED-UNIT — kg and lb sets for same lift across sessions */
    fun fixS1MixedUnit(): ResponseExtractionInput {
        val sessionKg = session("s-mixed-kg", baseInstant)
        val sessionLb = session("s-mixed-lb", baseInstant.plus(7, ChronoUnit.DAYS))
        val sets =
            listOf(
                loggedSet(
                    id = "set-kg",
                    sessionId = sessionKg.id,
                    liftId = liftBench,
                    weight = Weight(100.0, Weight.WeightUnit.Kg),
                    reps = 5,
                    rir = 2,
                    completedAt = sessionKg.startedAt.plusSeconds(60),
                ),
                loggedSet(
                    id = "set-lb",
                    sessionId = sessionLb.id,
                    liftId = liftBench,
                    weight = Weight(225.0, Weight.WeightUnit.Lb),
                    reps = 5,
                    rir = 2,
                    completedAt = sessionLb.startedAt.plusSeconds(60),
                ),
            )
        return input(
            sessions = listOf(sessionKg, sessionLb),
            sets = sets,
            lifts = listOf(benchPressLift()),
        )
    }

    /** FIX-S2-RIR-WEEK — 4 sets in one week, RIR values [1, 2, 3] and one null */
    fun fixS2RirWeek(): ResponseExtractionInput {
        val weekStart = weekStart(2025, 1, 6)
        val session = session("s2-rir-week", weekStart.plus(1, ChronoUnit.DAYS))
        val rirs = listOf(1, 2, 3, null)
        val sets =
            rirs.mapIndexed { index, rir ->
                loggedSet(
                    id = "rir-set-$index",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = rir,
                    completedAt = session.startedAt.plusSeconds((index * 90).toLong()),
                )
            }
        return input(sessions = listOf(session), sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S2-NO-RIR-WEEK — 4 working sets, zero RIR logged; peak e1RM normalized to 100 scale */
    fun fixS2NoRirWeek(): ResponseExtractionInput {
        val weekStart = weekStart(2025, 1, 6)
        val session = session("s2-no-rir", weekStart.plus(1, ChronoUnit.DAYS))
        val sets =
            (0 until 4).map { index ->
                loggedSet(
                    id = "no-rir-$index",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = null,
                    completedAt = session.startedAt.plusSeconds((index * 90).toLong()),
                )
            }
        return input(sessions = listOf(session), sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S2-OUTLIER-WEEKLY — 5 consecutive weeks e1RM peaks [100, 102, 200, 103, 104] */
    fun fixS2OutlierWeekly(): List<WeeklyTrainingSignal> =
        listOf(100.0, 102.0, 200.0, 103.0, 104.0).mapIndexed { index, peak ->
            weeklySignal(
                weekStart = weekStart(2025, 1, 6).plus(index * 7L, ChronoUnit.DAYS),
                liftId = liftBench,
                peakEstimatedOneRm = peak,
                smoothedEstimatedOneRm = peak,
            )
        }

    /** FIX-S3-GAP-45D — two blocks separated by 45 calendar days */
    fun fixS3Gap15d(): ResponseExtractionInput {
        val blockAEnd = baseInstant
        val blockBStart = blockAEnd.plus(45, ChronoUnit.DAYS)
        val sessionA = session("gap-a", blockAEnd.minus(1, ChronoUnit.DAYS))
        val sessionB = session("gap-b", blockBStart)
        val sets =
            listOf(
                loggedSet(
                    id = "gap-a-set",
                    sessionId = sessionA.id,
                    liftId = liftSquat,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = sessionA.startedAt.plusSeconds(60),
                ),
                loggedSet(
                    id = "gap-b-set",
                    sessionId = sessionB.id,
                    liftId = liftSquat,
                    weightKg = 105.0,
                    reps = 5,
                    rir = 2,
                    completedAt = sessionB.startedAt.plusSeconds(60),
                ),
            )
        return input(
            sessions = listOf(sessionA, sessionB),
            sets = sets,
            lifts = listOf(squatLift()),
        )
    }

    /** FIX-S3-GAP-14D — blocks exactly 14 calendar days apart (mutation boundary) */
    fun fixS3Gap14d(): ResponseExtractionInput {
        val blockAEnd = baseInstant
        val blockBStart = blockAEnd.plus(14, ChronoUnit.DAYS)
        val sessionA = session("gap14-a", blockAEnd)
        val sessionB = session("gap14-b", blockBStart)
        val sets =
            listOf(
                loggedSet(
                    id = "gap14-a-set",
                    sessionId = sessionA.id,
                    liftId = liftSquat,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = sessionA.startedAt.plusSeconds(60),
                ),
                loggedSet(
                    id = "gap14-b-set",
                    sessionId = sessionB.id,
                    liftId = liftSquat,
                    weightKg = 105.0,
                    reps = 5,
                    rir = 2,
                    completedAt = sessionB.startedAt.plusSeconds(60),
                ),
            )
        return input(
            sessions = listOf(sessionA, sessionB),
            sets = sets,
            lifts = listOf(squatLift()),
        )
    }

    /** FIX-S3-JACCARD — primary lift identity change > 0.5 Jaccard over 4 weeks */
    fun fixS3Jaccard(): ResponseExtractionInput {
        val weeks = (0 until 8).map { index ->
            session("jaccard-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
        }
        val sets = weeks.flatMapIndexed { weekIndex, session ->
            val liftId = if (weekIndex < 4) liftSquat else liftBench
            listOf(
                loggedSet(
                    id = "jaccard-set-$weekIndex",
                    sessionId = session.id,
                    liftId = liftId,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                ),
            )
        }
        return input(
            sessions = weeks,
            sets = sets,
            lifts = listOf(squatLift(), benchPressLift()),
        )
    }

    /** FIX-S3-LIFT-CHURN — primary lift roster rotates every 2 weeks over 8 weeks */
    fun fixS3LiftChurn(): ResponseExtractionInput {
        val ohpId = LiftId("lift-ohp")
        val rotatingLiftIds = listOf(liftSquat, liftBench, liftPull, ohpId)
        val lifts =
            listOf(
                squatLift(),
                benchPressLift(),
                lift(
                    id = liftPull,
                    name = "Barbell Row",
                    pattern = MovementPattern.HorizontalPull,
                    muscle = MuscleGroup.Back,
                ),
                lift(
                    id = ohpId,
                    name = "Overhead Press",
                    pattern = MovementPattern.VerticalPress,
                    muscle = MuscleGroup.AnteriorDeltoid,
                ),
            )
        val weeks =
            (0 until 8).map { index ->
                session("churn-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            weeks.flatMapIndexed { weekIndex, session ->
                val liftId = rotatingLiftIds[weekIndex / 2]
                listOf(
                    loggedSet(
                        id = "churn-set-$weekIndex",
                        sessionId = session.id,
                        liftId = liftId,
                        weightKg = 100.0,
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds(60),
                    ),
                )
            }
        return input(sessions = weeks, sets = sets, lifts = lifts)
    }

    /** FIX-S3-DELOAD-VOL — volume deload week at 40% of epoch median hard sets */
    fun fixS3DeloadVol(): ResponseExtractionInput {
        val sessions =
            (0 until 12).map { index ->
                session("deload-vol-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.flatMapIndexed { weekIndex, session ->
                val setCount = if (weekIndex == 6) 2 else 5
                (0 until setCount).map { setIndex ->
                    loggedSet(
                        id = "deload-vol-$weekIndex-$setIndex",
                        sessionId = session.id,
                        liftId = liftSquat,
                        weightKg = 100.0,
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds((setIndex * 60).toLong()),
                    )
                }
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S3-DELOAD-WGT — anchor weight −20%, volume steady */
    fun fixS3DeloadWgt(): ResponseExtractionInput {
        val sessions =
            (0 until 12).map { index ->
                session("deload-wgt-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.flatMapIndexed { weekIndex, session ->
                val weight = if (weekIndex == 6) 80.0 else 100.0
                (0 until 5).map { setIndex ->
                    loggedSet(
                        id = "deload-wgt-$weekIndex-$setIndex",
                        sessionId = session.id,
                        liftId = liftSquat,
                        weightKg = weight,
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds((setIndex * 60).toLong()),
                    )
                }
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S4-SPARSE-QUAD — 5 quad sessions over 4 weeks */
    fun fixS4SparseQuad(): ResponseExtractionInput {
        val sessions =
            (0 until 5).map { index ->
                session(
                    id = "sparse-quad-$index",
                    startedAt = baseInstant.plus(index * 5L, ChronoUnit.DAYS),
                )
            }
        val sets =
            sessions.mapIndexed { index, session ->
                loggedSet(
                    id = "sparse-set-$index",
                    sessionId = session.id,
                    liftId = liftSquat,
                    weightKg = 100.0 + index,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S4-RICH-CHEST — bench 10 sessions, fly 6 sessions over 8 weeks */
    fun fixS4RichChest(): ResponseExtractionInput {
        val benchSessions =
            (0 until 10).map { index ->
                session(
                    id = "rich-bench-$index",
                    startedAt = baseInstant.plus(index * 5L, ChronoUnit.DAYS),
                )
            }
        val flySessions =
            (0 until 6).map { index ->
                session(
                    id = "rich-fly-$index",
                    startedAt = baseInstant.plus(index * 9L, ChronoUnit.DAYS),
                )
            }
        val sessions = benchSessions + flySessions
        val benchSets =
            benchSessions.mapIndexed { index, session ->
                loggedSet(
                    id = "bench-set-$index",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        val flySets =
            flySessions.mapIndexed { index, session ->
                loggedSet(
                    id = "fly-set-$index",
                    sessionId = session.id,
                    liftId = liftFly,
                    weightKg = 30.0,
                    reps = 12,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(
            sessions = sessions,
            sets = benchSets + flySets,
            lifts = listOf(benchPressLift(), dumbbellFlyLift()),
        )
    }

    /** FIX-S5-SINGLE-EPOCH — one 10-week epoch, 5 weekly OLS points for Quad */
    fun fixS5SingleEpoch(): ResponseExtractionInput {
        val sessions =
            (0 until 10).map { index ->
                session("s5-single-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.flatMapIndexed { weekIndex, session ->
                listOf(
                    loggedSet(
                        id = "s5-single-set-$weekIndex",
                        sessionId = session.id,
                        liftId = liftSquat,
                        weightKg = 100.0 + weekIndex * 2,
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds(60),
                    ),
                )
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S5-THREE-WEEKLY-POINTS — 3 non-deload weekly OLS points for Quad */
    fun fixS5ThreeWeeklyPoints(): ResponseExtractionInput {
        val sessions =
            (0 until 3).map { index ->
                session("s5-3pts-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.mapIndexed { weekIndex, session ->
                loggedSet(
                    id = "s5-3pts-set-$weekIndex",
                    sessionId = session.id,
                    liftId = liftSquat,
                    weightKg = 100.0 + weekIndex * 2,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S5-SEVEN-WEEK-EPOCH — epoch spans 7 calendar weeks with 5 weekly points */
    fun fixS5SevenWeekEpoch(): ResponseExtractionInput {
        val windowEnd = baseInstant.plus(6 * 7L, ChronoUnit.DAYS)
        val sessions =
            (0 until 5).map { index ->
                session("s5-7w-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.mapIndexed { weekIndex, session ->
                loggedSet(
                    id = "s5-7w-set-$weekIndex",
                    sessionId = session.id,
                    liftId = liftSquat,
                    weightKg = 100.0 + weekIndex * 2,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(
            sessions = sessions,
            sets = sets,
            lifts = listOf(squatLift()),
            windowEnd = windowEnd,
        )
    }

    /** FIX-S5-SINGLE-EPOCH-DELOAD — 10-week epoch with 1 volume deload week and 5 OLS points */
    fun fixS5SingleEpochWithDeload(): ResponseExtractionInput {
        val sessions =
            (0 until 10).map { index ->
                session("s5-deload-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.flatMapIndexed { weekIndex, session ->
                val setCount = if (weekIndex == 6) 2 else 5
                (0 until setCount).map { setIndex ->
                    loggedSet(
                        id = "s5-deload-$weekIndex-$setIndex",
                        sessionId = session.id,
                        liftId = liftSquat,
                        weightKg = 100.0 + weekIndex * 2,
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds((setIndex * 60).toLong()),
                    )
                }
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(squatLift()))
    }

    /** FIX-S5-DUAL-EPOCH — two 10-week epochs, positive slope in both for Quad */
    fun fixS5DualEpoch(): ResponseExtractionInput {
        val gapDays = 45L
        val blockBStartDays = 10 * 7 + gapDays
        val squatSessions =
            buildList {
                (0 until 10).forEach { index ->
                    add(session("s5-dual-a-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS)))
                }
                (0 until 10).forEach { index ->
                    add(session("s5-dual-b-$index", baseInstant.plus(blockBStartDays + index * 7L, ChronoUnit.DAYS)))
                }
            }
        val benchSessions =
            squatSessions.mapIndexed { index, session ->
                session.copy(
                    id = TrainingSessionId("s5-dual-bench-${session.id.value}"),
                )
            }
        val sessions = squatSessions + benchSessions
        val squatSets =
            squatSessions.flatMapIndexed { weekIndex, session ->
                val setCount = if (weekIndex == 6) 2 else 5
                (0 until setCount).map { setIndex ->
                    loggedSet(
                        id = "s5-dual-set-$weekIndex-$setIndex",
                        sessionId = session.id,
                        liftId = liftSquat,
                        weightKg =
                            if (weekIndex == 6) {
                                100.0 + 5 * 3
                            } else {
                                100.0 + (weekIndex % 10) * 3
                            },
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds((setIndex * 60).toLong()),
                    )
                }
            }
        val benchSets =
            benchSessions.mapIndexed { weekIndex, session ->
                loggedSet(
                    id = "s5-dual-bench-set-$weekIndex",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0 + (weekIndex % 10) * 2,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(
            sessions = sessions,
            sets = squatSets + benchSets,
            lifts = listOf(squatLift(), benchPressLift()),
        )
    }

    /** FIX-S6-PATTERN-3 — HorizontalPress with 3 lifts, 8 sessions each; incline gated at 5 */
    fun fixS6Pattern3(): ResponseExtractionInput {
        val benchSessions =
            (0 until 8).map { index ->
                session("bench-s$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val dipSessions =
            (0 until 8).map { index ->
                session("dip-s$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val inclineSessions =
            (0 until 5).map { index ->
                session("incline-s$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val lifts =
            listOf(
                benchPressLift(),
                inclineBenchLift(),
                lift(
                    id = LiftId("lift-dip"),
                    name = "Weighted Dip",
                    pattern = MovementPattern.HorizontalPress,
                    muscle = MuscleGroup.Chest,
                ),
            )
        val sessions = benchSessions + dipSessions + inclineSessions
        val sets =
            buildList {
                benchSessions.forEachIndexed { index, session ->
                    add(
                        loggedSet(
                            id = "bench-${session.id.value}",
                            sessionId = session.id,
                            liftId = liftBench,
                            weightKg = 100.0 + index * 0.103,
                            reps = 5,
                            rir = 2,
                            completedAt = session.startedAt.plusSeconds(60),
                        ),
                    )
                }
                dipSessions.forEachIndexed { index, session ->
                    add(
                        loggedSet(
                            id = "dip-${session.id.value}",
                            sessionId = session.id,
                            liftId = LiftId("lift-dip"),
                            weightKg = 80.0 + index * 0.096,
                            reps = 5,
                            rir = 2,
                            completedAt = session.startedAt.plusSeconds(60),
                        ),
                    )
                }
                inclineSessions.forEach { session ->
                    add(
                        loggedSet(
                            id = "incline-${session.id.value}",
                            sessionId = session.id,
                            liftId = liftIncline,
                            weightKg = 90.0,
                            reps = 5,
                            rir = 2,
                            completedAt = session.startedAt.plusSeconds(60),
                        ),
                    )
                }
            }
        return input(sessions = sessions, sets = sets, lifts = lifts)
    }

    /** FIX-S6-SINGLE-LIFT — HorizontalPress with 1 lift, 8 sessions */
    fun fixS6SingleLift(): ResponseExtractionInput {
        val sessions =
            (0 until 8).map { index ->
                session("s6-single-$index", baseInstant.plus(index * 7L, ChronoUnit.DAYS))
            }
        val sets =
            sessions.map { session ->
                loggedSet(
                    id = "s6-single-${session.id.value}",
                    sessionId = session.id,
                    liftId = liftBench,
                    weightKg = 100.0,
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(sessions = sessions, sets = sets, lifts = listOf(benchPressLift()))
    }

    /** FIX-S6-WITH-VERTICAL — FIX-S6-PATTERN-3 plus 8 lat-pulldown sessions for VerticalPull ranking */
    fun fixS6WithVerticalPull(): ResponseExtractionInput {
        val base = fixS6Pattern3()
        val latLift =
            lift(
                id = liftVerticalPull,
                name = "Lat Pulldown",
                pattern = MovementPattern.VerticalPull,
                muscle = MuscleGroup.Back,
            )
        val latSessions =
            (0 until 8).map { index ->
                session(
                    id = "lat-s$index",
                    startedAt = baseInstant.plus(index * 7L, ChronoUnit.DAYS),
                )
            }
        val latSets =
            latSessions.map { session ->
                loggedSet(
                    id = "lat-${session.id.value}",
                    sessionId = session.id,
                    liftId = liftVerticalPull,
                    weightKg = 70.0,
                    reps = 8,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds(60),
                )
            }
        return input(
            sessions = base.sessions + latSessions,
            sets = base.sets + latSets,
            lifts = base.lifts + latLift,
            provenLiftIds = base.provenLiftIds,
            specializeMuscles = base.specializeMuscles,
            liftInsights = base.liftInsights,
            volumeBaselines = base.volumeBaselines,
            windowStart = base.analysisWindowStart,
            windowEnd = base.analysisWindowEnd,
        )
    }

    /** FIX-S7-MULTI-EPOCH — composite spanning slices 3–6 */
    fun fixS7MultiEpoch(provenLiftIds: Set<LiftId> = setOf(liftBench)): ResponseExtractionInput {
        val dual = fixS5DualEpoch()
        return dual.copy(provenLiftIds = provenLiftIds)
    }

    /** FIX-S9-IMPORT-OK — full import with Tier 1 + response success */
    fun fixS9ImportOk(): ResponseExtractionInput =
        fixS7MultiEpoch(provenLiftIds = setOf(liftBench))

    /** FIX-S10-PAYLOAD-MIX — observations covering all three EvidenceLevel values */
    fun fixS10PayloadMix(): ResponseExtractionInput = fixS7MultiEpoch()

    fun weeklySignal(
        weekStart: Instant,
        liftId: LiftId? = null,
        muscle: MuscleGroup? = null,
        hardSets: Double = 1.0,
        peakEstimatedOneRm: Double? = null,
        smoothedEstimatedOneRm: Double? = null,
        qualityFlags: Set<TrainingSignalQualityFlag> = emptySet(),
    ): WeeklyTrainingSignal =
        WeeklyTrainingSignal(
            weekStart = weekStart,
            liftId = liftId,
            muscle = muscle,
            hardSets = hardSets,
            sessions = 1,
            meanRir = null,
            intensityProxy = null,
            peakEstimatedOneRm = peakEstimatedOneRm,
            smoothedEstimatedOneRm = smoothedEstimatedOneRm ?: peakEstimatedOneRm,
            qualityFlags = qualityFlags,
        )

    fun epoch(
        id: String,
        start: Instant,
        end: Instant,
        splitReason: EpochSplitReason = EpochSplitReason.WindowEnd,
        anchorLiftIds: Set<LiftId> = emptySet(),
    ): Epoch =
        Epoch(
            id = EpochId(id),
            start = start,
            end = end,
            anchorLiftIds = anchorLiftIds,
            splitReason = splitReason,
        )

    fun input(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        lifts: List<Lift>,
        provenLiftIds: Set<LiftId> = emptySet(),
        specializeMuscles: Set<MuscleGroup> = emptySet(),
        liftInsights: List<LiftTrainingInsights> = emptyList(),
        volumeBaselines: List<MuscleVolumeBaseline> = emptyList(),
        windowStart: Instant = sessions.minOfOrNull { it.startedAt } ?: baseInstant,
        windowEnd: Instant = sessions.maxOfOrNull { it.startedAt }?.plus(1, ChronoUnit.DAYS) ?: baseInstant,
    ): ResponseExtractionInput =
        ResponseExtractionInput(
            sessions = sessions,
            sets = sets,
            lifts = lifts,
            provenLiftIds = provenLiftIds,
            specializeMuscles = specializeMuscles,
            liftInsights = liftInsights,
            volumeBaselines = volumeBaselines,
            analysisWindowStart = windowStart,
            analysisWindowEnd = windowEnd,
            activeExperiments = emptyList(),
        )

    private fun session(id: String, startedAt: Instant): TrainingSession =
        ImportHistoryTrainingMapper.parseSession(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt.plus(1, ChronoUnit.HOURS),
            sourceApp = "fixture",
        )

    private fun placeholderLiftContext(): LoggedLiftContext =
        LoggedLiftContext(
            liftName = "Fixture Exercise",
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Medium,
            muscleLengthBias = MuscleLengthBias.MidRange,
        )

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
        liftId: LiftId,
        weightKg: Double,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
    ): LoggedSet =
        loggedSet(
            id = id,
            sessionId = sessionId,
            liftId = liftId,
            weight = Weight(weightKg, Weight.WeightUnit.Kg),
            reps = reps,
            rir = rir,
            completedAt = completedAt,
        )

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
        liftId: LiftId,
        weight: Weight,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Fixture Exercise",
            liftId = liftId,
            setIndex = 1,
            weight = weight,
            reps = reps,
            rir = rir,
            completedAt = completedAt,
            liftContext = placeholderLiftContext(),
        )

    private fun lift(
        id: LiftId,
        name: String,
        pattern: MovementPattern,
        muscle: MuscleGroup,
    ): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(pattern),
            primaryMuscles = setOf(muscle),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
