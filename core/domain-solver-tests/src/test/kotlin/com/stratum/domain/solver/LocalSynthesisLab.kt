@file:Suppress("MagicNumber", "PrintStackTrace")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.universe.buildSlotUniverse
import com.stratum.domain.program.generation.structure.regionFiltersBySessionPosition
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.program.generation.volume.muscleTrainingDomain
import com.stratum.domain.program.generation.volume.TrainingDomain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Interactive CP-SAT lab — dumps a full synthesized program for local experimentation.
 *
 * ```
 * ./scripts/solver-local.sh lab
 * ./scripts/solver-local.sh lab -Dstratum.lab.days=5 -Dstratum.lab.layout=sean5
 * STRATUM_LAB_SPEC='Chest:Specialize,Back:Maintenance' ./scripts/solver-local.sh lab
 * ```
 *
 * Properties:
 * - `stratum.lab.days` (default 4)
 * - `stratum.lab.layout` (default sequential)
 * - `stratum.lab.session_minutes` (default 75)
 * - `stratum.lab.spec` comma list of `Muscle:Level` (default all Maintenance)
 * - `stratum.solver.deadline` ms (CpSatProgramSynthesizer also reads this if wired)
 */
class LocalSynthesisLab {

    @Test
    fun dumpProgram() = runBlocking {
        val days = System.getProperty("stratum.lab.days")?.toIntOrNull() ?: 4
        val layout = System.getProperty("stratum.lab.layout") ?: "sequential"
        val sessionMinutes = System.getProperty("stratum.lab.session_minutes")?.toIntOrNull() ?: 75
        val specialization = parseSpec(System.getProperty("stratum.lab.spec").orEmpty())
        val deadlineMs = System.getProperty("stratum.solver.deadline")?.toLongOrNull() ?: 60_000L

        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = days,
                    specialization = specialization,
                    sessionLengthMinutes = sessionMinutes,
                    weekdayLayout = layout,
                ),
            )
        val weeklyTargets = planWeeklyVolume(context)
        val universe = buildSlotUniverse(context, weeklyTargets)
        val filtersBySession = regionFiltersBySessionPosition(emptyList() /* cycle slots, not weekdays */)

        println("=== LocalSynthesisLab ===")
        println("days=$days layout=$layout sessionMinutes=$sessionMinutes deadlineMs=$deadlineMs")
        println("weekdays=${emptyList() /* cycle slots, not weekdays */}")
        println("specialization=$specialization")
        println(
            "regionFiltersBySession=" +
                filtersBySession.mapIndexed { i, allowed ->
                    "[$i]=${allowed.joinToString(",") { it.name }}"
                },
        )
        println("universeSlots=${universe.size}")
        val byRegionFilter =
            universe.groupBy { slot ->
                val hasUpper = SessionRegionFilter.UpperOnly in slot.allowedRegionFilters
                val hasLower = SessionRegionFilter.LowerOnly in slot.allowedRegionFilters
                when {
                    hasUpper && hasLower -> "both"
                    hasLower -> "lower"
                    hasUpper -> "upper"
                    else -> "unrestricted-only"
                }
            }
        println(
            "universeByRegionFilterEligibility=" +
                byRegionFilter.mapValues { it.value.size }.toSortedMap(),
        )
        val lowerSlots = universe.count { muscleTrainingDomain(it.primaryMuscle) == TrainingDomain.Lower }
        val upperSlots = universe.size - lowerSlots
        println("universePrimaryMuscle lower=$lowerSlots upper=$upperSlots")
        println("--- weekly aims (min/aim/max) ---")
        weeklyTargets.toSortedMap().forEach { (muscle, t) ->
            println(
                "  $muscle domain=${muscleTrainingDomain(muscle)} " +
                    "min=${"%.1f".format(t.min)} aim=${"%.1f".format(t.aim)} max=${"%.1f".format(t.max)}",
            )
        }

        val synthesizer = CpSatProgramSynthesizer(deadlineMillis = deadlineMs)
        var lastSynthesis: ProgramSynthesisResult? = null
        val capturingPort =
            object : ProgramSynthesisPort {
                override suspend fun synthesize(
                    weeklyTargets: com.stratum.domain.program.generation.volume.WeeklyVolumeTargets,
                    context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
                ): ProgramSynthesisResult =
                    synthesizer.synthesize(weeklyTargets, context).also { lastSynthesis = it }
            }
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = capturingPort)
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = normalized.toDraft()
        val program = normalized.toDomainProgram()
        val schedule = buildWeeklySchedule(program)

        println(
            "regionFilterBySession(solved)=" +
                (lastSynthesis?.regionFilterBySession?.joinToString(",") { it.name } ?: "n/a"),
        )

        println("--- schedule ---")
        for (item in schedule) {
            when (item) {
                is ScheduleDayItem.WorkoutDay -> {
                    val lifts =
                        item.workout.workoutLifts.joinToString("; ") { wl ->
                            val name = context.liftsById[wl.liftId.value]?.name ?: wl.liftId.value
                            "$name×${wl.sets.size}"
                        }
                    println("${item.weekday.name}: ${item.workout.name}  [$lifts]")
                }
                is ScheduleDayItem.RestDay -> println("${item.weekday.name}: REST")
            }
        }

        println("--- weekly volume (primary+secondary credits) ---")
        val weekly = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                weekly[muscle.name] = (weekly[muscle.name] ?: 0.0) + dose
            }
        }
        weekly.toSortedMap().forEach { (muscle, vol) ->
            val target = weeklyTargets[muscle]
            val aim = target?.aim
            val delta = if (aim != null) vol - aim else null
            println(
                "  $muscle domain=${muscleTrainingDomain(muscle)} " +
                    "vol=${"%.1f".format(vol)}" +
                    (if (aim != null) " aim=${"%.1f".format(aim)} Δ=${"%+.1f".format(delta)}" else ""),
            )
        }

        val upperVol =
            weekly.filterKeys { muscleTrainingDomain(it) == TrainingDomain.Upper }.values.sum()
        val lowerVol =
            weekly.filterKeys { muscleTrainingDomain(it) == TrainingDomain.Lower }.values.sum()
        println("--- domain totals upper=${"%.1f".format(upperVol)} lower=${"%.1f".format(lowerVol)} ---")
        println("=== end LocalSynthesisLab ===")
    }

    private fun parseSpec(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { token ->
                val parts = token.split(':')
                require(parts.size == 2) {
                    "Bad stratum.lab.spec entry '$token' (expected Muscle:Level)"
                }
                parts[0].trim() to parts[1].trim()
            }
    }
}
