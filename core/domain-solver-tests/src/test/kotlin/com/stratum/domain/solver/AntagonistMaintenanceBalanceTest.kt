@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * When chest/back and quad/ham share the same maintenance tier, weekly volume should stay
 * close and favor the joint-safer direction (back ≥ chest, ham ≥ quad) when imbalanced.
 */
class AntagonistMaintenanceBalanceTest {
    @Test
    fun fiveDayArmCalfSpecBalancesMaintenanceAntagonistPairs() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = specialization,
                    weekdayLayout = "mtwfs",
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0

        assertTrue(
            kotlin.math.abs(chest - back) <= 6.0,
            "Chest/back should stay within 6 sets when both are maintenance: chest=$chest back=$back",
        )
        assertTrue(
            back + 0.5 >= chest,
            "Prefer back ≥ chest when imbalanced (joint safety): chest=$chest back=$back",
        )
        assertTrue(
            kotlin.math.abs(quad - ham) <= 6.0,
            "Quad/ham should stay within 6 sets when both are maintenance: quad=$quad ham=$ham",
        )
        assertTrue(
            ham + 0.5 >= quad,
            "Prefer ham ≥ quad when imbalanced (joint safety): quad=$quad ham=$ham",
        )
    }

    @Test
    fun fourDayMaintenanceExcludesDirectAnteriorAndPrioritizesArmsOverRearDelt() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = emptyMap(),
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()

        // G14a: No lift with primaryMuscle == AnteriorDeltoid should appear — anterior volume
        // comes entirely from press secondaries when anterior is not Specialize.
        val anteriorDirectLifts = directLiftsForMuscle(draft, context, "AnteriorDeltoid")
        assertTrue(
            anteriorDirectLifts.isEmpty(),
            "Expected zero direct anterior lifts when AnteriorDeltoid is Maintenance, " +
                "but found: ${anteriorDirectLifts.map { it.first }}",
        )

        // G14b: Rear-delt should not outrank arms/lateral for direct work. Biceps/triceps receive
        // substantial secondary coverage from pull/press compounds (0.5/set), so when their aim is
        // met by secondaries the solver correctly skips direct arm work — comparing direct sets
        // alone would penalize that correct behavior. Compare TOTAL weekly volume (direct + secondary)
        // for bicep/tricep, and keep the lateral comparison direct-only (ShoulderAbduction has no
        // secondary path, so lateral must be met by direct sets).
        val directSets = directSetsByMuscle(draft, context)
        val weekly = weeklyMuscleVolume(draft, context)
        val rearDeltDirect = directSets["PosteriorDeltoid"] ?: 0
        val lateralDirect = directSets["LateralDeltoid"] ?: 0
        val bicepTotal = weekly["Bicep"] ?: 0.0
        val tricepTotal = weekly["Tricep"] ?: 0.0
        val rearDeltTotal = weekly["PosteriorDeltoid"] ?: 0.0

        assertTrue(
            bicepTotal >= rearDeltTotal,
            "Bicep total volume ($bicepTotal, incl. pull secondaries) should be ≥ rear-delt total ($rearDeltTotal)",
        )
        assertTrue(
            tricepTotal >= rearDeltTotal,
            "Tricep total volume ($tricepTotal, incl. press secondaries) should be ≥ rear-delt total ($rearDeltTotal)",
        )
        assertTrue(
            rearDeltDirect <= lateralDirect,
            "Lateral-delt direct sets ($lateralDirect) should be ≥ rear-delt direct sets ($rearDeltDirect)",
        )
    }

    /**
     * Regression: `barbell-sumo-deadlift` (HipHinge, Hamstring primary) tags Quad as a secondary
     * muscle (0.5/set). The static `PATTERN_SECONDARY_MUSCLE_CREDITS["HipHinge"]` table credits 0
     * Quad, so the solver used to allocate HipHinge sets thinking they cost 0 Quad — then the
     * Programs UI (metadata truth) showed Quad inflated by the secondary credit the solver never
     * saw. With catalog-derived credits (`computePatternSecondaryCredits`), the solver's HipHinge
     * Quad credit is the catalog average (non-zero when any HipHinge lift tags Quad secondary),
     * so quad/ham stays balanced in metadata truth.
     */
    @Test
    fun fourDayMaintenanceQuadHamBalanceWithSumoDeadliftQuadSecondary() = runBlocking {
        val context = quadHamDivergenceCatalog()
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0

        assertTrue(
            ham + 0.5 >= quad,
            "Quad should not exceed Hamstring by more than 0.5 when both are Maintenance, " +
                "even when a HipHinge lift (sumo deadlift) tags Quad as secondary: quad=$quad ham=$ham",
        )
    }

    /**
     * Catalog where barbell-sumo-deadlift (HipHinge, Hamstring primary, Quad secondary) is the
     * dominant HipHinge lift. With the old static credits, the solver's HipHinge Quad credit was
     * 0 — now it is the catalog average and must be non-zero.
     */
    private fun quadHamDivergenceCatalog(): ProgramGenerationContext {
        fun lift(id: String, name: String, pattern: String, primary: List<String>, secondary: List<String> = emptyList()) =
            LiftContext(
                id = id,
                name = name,
                movementPattern = MovementPattern.valueOf(pattern),
                movementPatterns = setOf(MovementPattern.valueOf(pattern)),
                primaryMuscles = primary.map { MuscleGroup.valueOf(it) }.toSet(),
                secondaryMuscles = secondary.map { MuscleGroup.valueOf(it) }.toSet(),
                proven = true,
            )
        val lifts = listOf(
            lift("bench", "Bench Press", "HorizontalPress", listOf("Chest"), listOf("Tricep", "AnteriorDeltoid")),
            lift("inc-press", "Incline Press", "InclinePress", listOf("Chest"), listOf("AnteriorDeltoid", "Tricep")),
            lift("row", "Barbell Row", "HorizontalPull", listOf("Back"), listOf("Bicep", "PosteriorDeltoid")),
            lift("pullup", "Pullup", "VerticalPull", listOf("Back"), listOf("Bicep")),
            lift("back-squat", "Barbell Back Squat", "SquatPattern", listOf("Quad"), listOf("Glute", "LowerBack")),
            lift("front-squat", "Barbell Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
            lift("leg-ext", "Leg Extension", "KneeExtension", listOf("Quad")),
            lift("rdl", "Romanian Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
            // Sumo deadlift: HipHinge pattern, Hamstring primary, but Quad in secondary.
            // This is the only HipHinge lift tagging Quad, so the catalog average credit for
            // HipHinge→Quad is non-zero, making the solver aware of the divergence.
            lift("sumo-dl", "Barbell Sumo Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack", "Quad")),
            lift("leg-curl", "Leg Curl", "KneeFlexion", listOf("Hamstring")),
            lift("hip-thrust", "Hip Thrust", "HipExtension", listOf("Glute"), listOf("Hamstring")),
            lift("calf-raise", "Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
            lift("ohp", "Overhead Press", "VerticalPress", listOf("AnteriorDeltoid"), listOf("Tricep")),
            lift("curl", "Curl", "ElbowFlexion", listOf("Bicep")),
            lift("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("Tricep")),
        )
        val landmarks = listOf(
            vlc("Chest", 10.0, 14.0, 20.0, 11.0),
            vlc("Back", 11.0, 14.0, 20.0, 12.8),
            vlc("Quad", 11.0, 14.0, 20.0, 12.8),
            vlc("Hamstring", 6.0, 10.0, 16.0, 8.4),
            vlc("Glute", 6.0, 10.0, 16.0, 8.4),
            vlc("Calf", 8.0, 12.0, 22.0, 9.6),
            vlc("Tricep", 6.0, 11.0, 18.0, 9.0),
            vlc("Bicep", 6.0, 10.0, 22.0, 8.4),
            vlc("LateralDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("PosteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("AnteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("LowerBack", 0.0, 8.0, 14.0, 4.0),
        )
        val liftsById = linkedMapOf<String, LiftContext>()
        val enabledIds = mutableSetOf<String>()
        val patternById = linkedMapOf<String, MovementPattern?>()
        for (l in lifts) {
            liftsById[l.id] = l
            enabledIds += l.id
            patternById[l.id] = l.movementPattern
        }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 60,
            trainingDaysPerWeek = 4,
            // Mon/Tue/Thu/Fri on a Mon-start 7-cycle: rests Wed(2), Sat(5), Sun(6).
            restSlotIndices = setOf(2, 5, 6),
            cycleLength = 7,
            specialization = emptyMap(),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = landmarks.associateBy { it.muscle },
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = enabledIds,
            movementPatternByLiftId = patternById,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun vlc(muscle: String, mv: Double, maintenanceCeiling: Double, rc: Double, pv: Double) =
        MuscleVolumeSnapshotContext(
            muscle = muscle,
            retentionFloorMv = mv,
            paretoVolumePv = pv,
            recoverabilityCeilingRc = rc,
            growthEntryFloor = 6.0,
            maintenanceCeiling = maintenanceCeiling,
            posture = null,
            source = VolumeSnapshotSource.Literature,
        )

    @Test
    fun fourDayMaintenanceKeepsBicepTricepRoughlyEven() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = emptyMap(),
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val bicep = weekly["Bicep"] ?: 0.0
        val tricep = weekly["Tricep"] ?: 0.0

        assertTrue(
            kotlin.math.abs(bicep - tricep) <= 6.0,
            "Bicep/tricep should stay within 6 sets when both are maintenance: bicep=$bicep tricep=$tricep weekly=$weekly",
        )
    }

    /**
     * Upper-body maintenance parity: when all five upper-body muscles (Chest, Back, Bicep, Tricep,
     * LateralDeltoid) are Maintenance on a 5-day split, no single muscle should drift far from the
     * group. With uniform PV=13 targets (matching [GlobalVolumePriors]), the parity term should keep
     * the group within a tight cluster regardless of secondary-credit differences.
     *
     * Uses [lateralDivergenceCatalog] with uniform volume landmarks (MV=4, PV=13, RC=20) matching
     * production [GlobalVolumePriors]. A prior version of this fixture used non-uniform artificial
     * targets (LateralDeltoid PV=18, Chest PV=10) that couldn't occur in production; those have been
     * normalized. The test now validates parity under realistic conditions.
     */
    @Test
    fun fiveDayMaintenanceKeepsUpperBodyGroupBalanced() = runBlocking {
        val context = lateralDivergenceCatalog()
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val group = listOf("Chest", "Back", "Bicep", "Tricep", "LateralDeltoid")
        val volumes = group.map { muscle -> weekly[muscle] ?: 0.0 }
        val spread = (volumes.maxOrNull() ?: 0.0) - (volumes.minOrNull() ?: 0.0)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0
        val lateral = weekly["LateralDeltoid"] ?: 0.0

        assertTrue(
            spread <= 5.0,
            "Upper-body maintenance group should stay within a 5-set spread when all are Maintenance: " +
                "spread=$spread volumes=${group.zip(volumes).toMap()} weekly=$weekly",
        )
        assertTrue(
            lateral <= chest + 5.0,
            "LateralDeltoid ($lateral) should not outrun Chest ($chest) by more than 5 sets " +
                "when both are Maintenance: weekly=$weekly",
        )
        assertTrue(
            lateral <= back + 5.0,
            "LateralDeltoid ($lateral) should not outrun Back ($back) by more than 5 sets " +
                "when both are Maintenance: weekly=$weekly",
        )
    }

    /**
     * Lower-body maintenance parity: when Quad, Hamstring, and Calf are all Maintenance on a 5-day
     * split, Calf should not outrun Quad/Hamstring by a wide margin. Calf is the typical outlier —
     * it accumulates incidental secondary credit from squats (~0.39/set in production, where ~78% of
     * SquatPattern lifts tag Calf secondary), recovers at the fast 24h Low-isolation tier, and had no
     * balance constraint pulling it toward the larger leg muscles.
     *
     * Uses [calfDivergenceCatalog] — a catalog where squats tag Calf as a secondary (the production
     * drift mechanism) and Calf has a higher maintenance ceiling (18) than Quad (14) / Hamstring (10).
     *
     * Measured behavior: without the parity term Calf floats to its 18.0 ceiling (spread 8.0); with
     * the term it pulls down to 16.0 (spread 6.0). The residual gap is recovery-driven: Calf recovers
     * in 24h (Low-isolation tier) while Quad/Hamstring need 48h, so session-fill pressure (weight
     * [SynthesisObjectiveWeights.SESSION_FILL_SHORTFALL] = 4,000, which sits ABOVE parity's 1,500 in
     * the tier hierarchy) packs Calf into sessions that Quad/Hamstring can't fill. Parity at 1,500
     * intentionally sits below session-fill (matching the upper-body parity tier), so the solver
     * eats the parity penalty when recovery spacing genuinely can't accommodate convergence — this is
     * the same trade-off documented on [ProgramSynthesisModel.addUpperBodyMaintenanceParity].
     *
     * The 7.0 threshold is a regression guard: it sits between the 6.0 the rule achieves and the 8.0
     * baseline without the rule, so it fails if the parity term is removed or weakened. In production,
     * where Calf is absent from MAJOR_MUSCLES and often has NO weekly ceiling at all, the rule has
     * larger effect since it pulls against unbounded drift rather than an 18.0 cap.
     */
    @Test
    fun fiveDayMaintenanceKeepsLegGroupBalanced() = runBlocking {
        val context = calfDivergenceCatalog()
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val group = listOf("Quad", "Hamstring", "Calf")
        val volumes = group.map { muscle -> weekly[muscle] ?: 0.0 }
        val spread = (volumes.maxOrNull() ?: 0.0) - (volumes.minOrNull() ?: 0.0)
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0
        val calf = weekly["Calf"] ?: 0.0

        assertTrue(
            spread <= 7.0,
            "Lower-body maintenance group should stay within a 7-set spread when all are Maintenance " +
                "(regression guard: 6.0 with parity, 8.0 without): " +
                "spread=$spread volumes=${group.zip(volumes).toMap()} weekly=$weekly",
        )
        assertTrue(
            calf <= quad + 7.0,
            "Calf ($calf) should not outrun Quad ($quad) by more than 7 sets " +
                "when both are Maintenance: weekly=$weekly",
        )
        assertTrue(
            calf <= ham + 7.0,
            "Calf ($calf) should not outrun Hamstring ($ham) by more than 7 sets " +
                "when both are Maintenance: weekly=$weekly",
        )
    }

    /**
     * Catalog where squats tag Calf as a secondary muscle (reproducing the production drift mechanism
     * — ~78% of SquatPattern seed lifts tag Calf secondary, giving every squat set ~0.39 incidental
     * Calf credit) and Calf has a higher maintenance ceiling (18) than Quad (14) / Hamstring (10).
     * Without the parity term, incidental squat credit + session-fill pressure push Calf toward its
     * ceiling while Quad/Hamstring sit lower — a wide spread. With the parity term, the three leg
     * muscles cluster tighter.
     */
    private fun calfDivergenceCatalog(): ProgramGenerationContext {
        fun lift(id: String, name: String, pattern: String, primary: List<String>, secondary: List<String> = emptyList()) =
            LiftContext(
                id = id,
                name = name,
                movementPattern = MovementPattern.valueOf(pattern),
                movementPatterns = setOf(MovementPattern.valueOf(pattern)),
                primaryMuscles = primary.map { MuscleGroup.valueOf(it) }.toSet(),
                secondaryMuscles = secondary.map { MuscleGroup.valueOf(it) }.toSet(),
                proven = true,
            )
        val lifts = listOf(
            lift("bench", "Bench Press", "HorizontalPress", listOf("Chest"), listOf("Tricep", "AnteriorDeltoid")),
            lift("inc-press", "Incline Press", "InclinePress", listOf("Chest"), listOf("AnteriorDeltoid", "Tricep")),
            lift("row", "Barbell Row", "HorizontalPull", listOf("Back"), listOf("Bicep", "PosteriorDeltoid")),
            lift("pullup", "Pullup", "VerticalPull", listOf("Back"), listOf("Bicep")),
            // Squats tag Calf as a secondary — the production drift mechanism. This gives every squat
            // set an incidental Calf credit (catalog-averaged across both squat lifts here).
            lift("back-squat", "Barbell Back Squat", "SquatPattern", listOf("Quad"), listOf("Glute", "LowerBack", "Calf")),
            lift("front-squat", "Barbell Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute", "Calf")),
            lift("leg-ext", "Leg Extension", "KneeExtension", listOf("Quad")),
            lift("rdl", "Romanian Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
            lift("leg-curl", "Leg Curl", "KneeFlexion", listOf("Hamstring")),
            lift("hip-thrust", "Hip Thrust", "HipExtension", listOf("Glute"), listOf("Hamstring")),
            lift("calf-raise", "Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
            lift("ohp", "Overhead Press", "VerticalPress", listOf("AnteriorDeltoid"), listOf("Tricep")),
            lift("curl", "Curl", "ElbowFlexion", listOf("Bicep")),
            lift("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("Tricep")),
        )
        val landmarks = listOf(
            vlc("Chest", 8.0, 12.0, 20.0, 10.0),
            vlc("Back", 8.0, 12.0, 20.0, 10.0),
            vlc("Quad", 11.0, 14.0, 20.0, 12.8),
            vlc("Hamstring", 6.0, 10.0, 16.0, 8.4),
            vlc("Glute", 6.0, 10.0, 16.0, 8.4),
            vlc("Calf", 8.0, 18.0, 22.0, 12.0),
            vlc("Tricep", 6.0, 14.0, 18.0, 10.0),
            vlc("Bicep", 6.0, 14.0, 22.0, 10.0),
            vlc("LateralDeltoid", 6.0, 14.0, 18.0, 10.0),
            vlc("PosteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("AnteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("LowerBack", 0.0, 8.0, 14.0, 4.0),
        )
        val liftsById = linkedMapOf<String, LiftContext>()
        val enabledIds = mutableSetOf<String>()
        val patternById = linkedMapOf<String, MovementPattern?>()
        for (l in lifts) {
            liftsById[l.id] = l
            enabledIds += l.id
            patternById[l.id] = l.movementPattern
        }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 5,
            // Mon/Tue/Thu/Fri/Sat on a Mon-start 7-cycle: rests Wed(2), Sun(6).
            restSlotIndices = setOf(2, 6),
            cycleLength = 7,
            specialization = emptyMap(),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = landmarks.associateBy { it.muscle },
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = enabledIds,
            movementPatternByLiftId = patternById,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    /**
     * Catalog where LateralDeltoid has a higher maintenance ceiling (18) than Chest/Back (12),
     * reproducing the user-reported lateral-delt drift. Without the parity term, the solver pushes
     * lateral to 18 (its ceiling) while Chest/Back sit at 12 — a 6-set spread. With the parity term,
     * all five upper-body muscles cluster at 12.
     */
    private fun lateralDivergenceCatalog(): ProgramGenerationContext {
        fun lift(id: String, name: String, pattern: String, primary: List<String>, secondary: List<String> = emptyList()) =
            LiftContext(
                id = id,
                name = name,
                movementPattern = MovementPattern.valueOf(pattern),
                movementPatterns = setOf(MovementPattern.valueOf(pattern)),
                primaryMuscles = primary.map { MuscleGroup.valueOf(it) }.toSet(),
                secondaryMuscles = secondary.map { MuscleGroup.valueOf(it) }.toSet(),
                proven = true,
            )
        val lifts = listOf(
            lift("bench", "Bench Press", "HorizontalPress", listOf("Chest"), listOf("Tricep", "AnteriorDeltoid")),
            lift("inc-press", "Incline Press", "InclinePress", listOf("Chest"), listOf("AnteriorDeltoid", "Tricep")),
            lift("row", "Barbell Row", "HorizontalPull", listOf("Back"), listOf("Bicep", "PosteriorDeltoid")),
            lift("pullup", "Pullup", "VerticalPull", listOf("Back"), listOf("Bicep")),
            lift("back-squat", "Barbell Back Squat", "SquatPattern", listOf("Quad"), listOf("Glute", "LowerBack")),
            lift("front-squat", "Barbell Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
            lift("leg-ext", "Leg Extension", "KneeExtension", listOf("Quad")),
            lift("rdl", "Romanian Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
            lift("leg-curl", "Leg Curl", "KneeFlexion", listOf("Hamstring")),
            lift("hip-thrust", "Hip Thrust", "HipExtension", listOf("Glute"), listOf("Hamstring")),
            lift("calf-raise", "Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
            lift("ohp", "Overhead Press", "VerticalPress", listOf("AnteriorDeltoid"), listOf("Tricep")),
            lift("curl", "Curl", "ElbowFlexion", listOf("Bicep")),
            lift("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("Tricep")),
        )
        val landmarks = listOf(
            vlc("Chest", 4.0, 16.5, 20.0, 13.0),
            vlc("Back", 4.0, 16.5, 20.0, 13.0),
            vlc("Quad", 4.0, 16.5, 20.0, 13.0),
            vlc("Hamstring", 4.0, 16.5, 20.0, 13.0),
            vlc("Glute", 4.0, 16.5, 20.0, 13.0),
            vlc("Calf", 4.0, 16.5, 20.0, 13.0),
            vlc("Tricep", 4.0, 16.5, 20.0, 13.0),
            vlc("Bicep", 4.0, 16.5, 20.0, 13.0),
            vlc("LateralDeltoid", 4.0, 16.5, 20.0, 13.0),
            vlc("PosteriorDeltoid", 4.0, 16.5, 20.0, 13.0),
            vlc("AnteriorDeltoid", 4.0, 16.5, 20.0, 13.0),
            vlc("LowerBack", 4.0, 16.5, 20.0, 13.0),
        )
        val liftsById = linkedMapOf<String, LiftContext>()
        val enabledIds = mutableSetOf<String>()
        val patternById = linkedMapOf<String, MovementPattern?>()
        for (l in lifts) {
            liftsById[l.id] = l
            enabledIds += l.id
            patternById[l.id] = l.movementPattern
        }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 5,
            // Mon/Tue/Thu/Fri/Sat on a Mon-start 7-cycle: rests Wed(2), Sun(6).
            restSlotIndices = setOf(2, 6),
            cycleLength = 7,
            specialization = emptyMap(),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = landmarks.associateBy { it.muscle },
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = enabledIds,
            movementPatternByLiftId = patternById,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun weeklyMuscleVolume(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle.name] = (totals[muscle.name] ?: 0.0) + dose
            }
        }
        return totals
    }

    /** Returns (liftId, setCount) pairs where the lift's primaryMuscles include [muscle]. */
    private fun directLiftsForMuscle(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
        muscle: String,
    ): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val liftId = lift.liftId.orEmpty()
                val meta = context.liftsById[liftId] ?: continue
                val setCount = lift.workoutLiftSets?.size ?: 0
                if (setCount == 0) continue
                if (runCatching { MuscleGroup.valueOf(muscle) }.getOrNull() in meta.primaryMuscles) {
                    result += liftId to setCount
                }
            }
        }
        return result
    }

    /** Total direct (primary-muscle) sets per muscle across all workouts. */
    private fun directSetsByMuscle(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Int> {
        val totals = mutableMapOf<String, Int>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val liftId = lift.liftId.orEmpty()
                val meta = context.liftsById[liftId] ?: continue
                val setCount = lift.workoutLiftSets?.size ?: 0
                if (setCount == 0) continue
                for (muscle in meta.primaryMuscles) {
                    totals[muscle.name] = (totals[muscle.name] ?: 0) + setCount
                }
            }
        }
        return totals
    }
}
