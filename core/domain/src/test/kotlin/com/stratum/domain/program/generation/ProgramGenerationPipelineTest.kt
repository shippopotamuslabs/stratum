package com.stratum.domain.program.generation

import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.program.GENERATION_SCENARIOS
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.ScenarioConfig
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.frequencyTierForWeeklySets
import com.stratum.domain.program.generation.reference.sessionDurationBudgetMinutes
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ProgramGenerationPipelineTest {

  private val pipeline = testProgramGenerationPipeline()

  @ParameterizedTest(name = "{0}")
  @MethodSource("generationScenarios")
  @DisplayName("cycle structure and no duplicate lifts")
  fun cycleStructureAndNoDuplicateLifts(config: ScenarioConfig) = runTest {
    val (program, _) = generateScenario(config)
    assertEquals(config.days, program.cycleLength)
    assertEquals(config.days, program.workouts.size)
    for (workout in program.workouts) {
      assertEquals(null, workout.weekday)
      val ids = workout.workoutLifts.map { it.liftId }
      assertEquals(ids.size, ids.toSet().size, "${workout.name} has duplicate liftIds")
      assertTrue(ids.size >= 3, "${workout.name} should have enough exercises to fill a session")
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generationScenarios")
  @DisplayName("≤8 hard sets per muscle per session (primary + secondary credits)")
  fun weightologySessionCaps(config: ScenarioConfig) = runTest {
    val (program, context) = generateScenario(config)
    assertWeightologySessionCaps(program.toDraft(), context, config.label)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generationScenarios")
  @DisplayName("weekly volume targets from landmarks (not time-budget scaled)")
  fun weeklyVolumeTargetsFromLandmarks(config: ScenarioConfig) = runTest {
    val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
    val context = parseScenarioContext(scenarioPayload(config.days, specialization))
    val targets = planWeeklyVolume(context)

    if (specialization["Chest"] == "Specialize") {
      assertTrue((targets["Chest"]?.aim ?: 0.0) >= 14.0)
    } else if (specialization["Back"] != "Specialize") {
      assertEquals(14.0, targets["Back"]?.aim)
    }

    if (specialization["Back"] == "Specialize") {
      assertTrue((targets["Back"]?.aim ?: 0.0) >= 14.0)
    } else if (specialization["Chest"] != "Specialize") {
      assertEquals(14.0, targets["Chest"]?.aim)
    }

    if (specialization["Bicep"] == "Specialize" || specialization["Tricep"] == "Specialize") {
      val neutralContext = parseScenarioContext(
        scenarioPayload(config.days, mapOf("Bicep" to "Maintenance", "Tricep" to "Maintenance")),
      )
      val neutral = planWeeklyVolume(neutralContext)
      if (specialization["Bicep"] == "Specialize") {
        assertTrue((targets["Bicep"]?.aim ?: 0.0) > (neutral["Bicep"]?.aim ?: 0.0))
      }
      if (specialization["Tricep"] == "Specialize") {
        assertTrue((targets["Tricep"]?.aim ?: 0.0) > (neutral["Tricep"]?.aim ?: 0.0))
      }
    }
  }

  @ParameterizedTest
  @MethodSource("maintenanceDayCounts")
  @DisplayName("muscles above 10 weekly sets trained ≥2× per cycle")
  fun frequencyMatchesVolume(days: Int) = runTest {
    val config = ScenarioConfig(days = days, preset = "maintenance", label = "$days-day maintenance")
    val (program, context) = generateScenario(config)
    assertFrequencyMatchesVolume(program.toDraft(), context, config.label)
  }

  @Test
  @DisplayName("4-day arm specialization: back weekly target ~14, split across upper days")
  fun fourDayArmSpecializationBackVolume() = runTest {
    val config = ScenarioConfig(days = 4, preset = "arms", label = "4-day arms")
    val (program, context) = generateScenario(config)
    val targets = planWeeklyVolume(context)
    assertEquals(14.0, targets["Back"]?.aim)

    val draft = program.toDraft()
    val upperWorkouts = draft.workouts.filter { (it.name).orEmpty().contains("upper", ignoreCase = true) }
    assertTrue(upperWorkouts.size >= 2)
    var totalBack = 0
    for (workout in upperWorkouts) {
      val backSets = primarySetsByMuscleInWorkout(workout, context)["Back"] ?: 0
      totalBack += backSets
      assertTrue(backSets <= SESSION_MUSCLE_SET_CAP, "${workout.name} back sets $backSets")
    }
    assertTrue(totalBack >= 8, "back should accumulate meaningful weekly volume across upper days")
  }

  @ParameterizedTest
  @MethodSource("dayCounts")
  @DisplayName("30-minute sessions stay within session length + 5 minutes")
  fun shortSessionsStayWithinBudget(days: Int) = runTest {
    val payload = scenarioPayload(days, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"))
    val body = Json.parseToJsonElement(payload).jsonObject.toMutableMap()
    val profile = body["profile"]!!.jsonObject.toMutableMap()
    profile["sessionLengthMinutes"] = JsonPrimitive(30)
    body["profile"] = JsonObject(profile)
    val context = parseScenarioContext(Json.encodeToString(JsonObject.serializer(), JsonObject(body)))
    val program = pipeline.generate(
      context = context,
      normalizationRequest = context.toProgramNormalizationRequest(),
      options = PipelineOptions(skipLiftSwap = true),
    )
    val report = validateProgramMetrics(program.toDraft(), context)
    val budget = sessionDurationBudgetMinutes(30).toDouble()
    report.summary.estimatedWorkoutMinutes.forEachIndexed { index, minutes ->
      val name = program.workouts.getOrNull(index)?.name ?: "workout $index"
      assertTrue(
        minutes <= budget + 0.01,
        "$name: ${"%.1f".format(minutes)} min exceeds $budget min budget (30 + 5 slack)",
      )
    }
  }

  private suspend fun generateScenario(
    config: ScenarioConfig,
  ): Pair<com.stratum.domain.program.generation.schema.NormalizedProgram, com.stratum.domain.program.generation.context.ProgramGenerationContext> {
    val specialization = SPECIALIZATION_PRESETS[config.preset].orEmpty()
    val context = parseScenarioContext(scenarioPayload(config.days, specialization))
    val program = pipeline.generate(
      context = context,
      normalizationRequest = context.toProgramNormalizationRequest(),
      options = PipelineOptions(skipLiftSwap = true),
    )
    return program to context
  }

  private fun assertWeightologySessionCaps(
    program: com.stratum.domain.program.generation.draft.ProgramDraft,
    context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
    scenarioLabel: String,
  ) {
    val report = validateProgramMetrics(program, context)
    val sessionCapErrors = report.issues.filter { it.code == "SESSION_MUSCLE_VOLUME_HIGH" }
    assertEquals(
      0,
      sessionCapErrors.size,
      "$scenarioLabel: fractional session volume exceeds $SESSION_MUSCLE_SET_CAP — " +
        sessionCapErrors.joinToString("; ") { it.message },
    )

    for (workout in program.workouts) {
      val primary = primarySetsByMuscleInWorkout(workout, context)
      for ((muscle, sets) in primary) {
        assertTrue(
          sets <= SESSION_MUSCLE_SET_CAP,
          "$scenarioLabel: ${workout.name} has $sets primary sets for $muscle (cap $SESSION_MUSCLE_SET_CAP)",
        )
      }
      val fractional = computeWorkoutMuscleVolume(workout, context)
      for ((muscle, credits) in fractional) {
        assertTrue(
          credits <= SESSION_MUSCLE_SET_CAP + 0.01,
          "$scenarioLabel: ${workout.name} has $credits fractional credits for $muscle",
        )
      }
    }
  }

  private fun assertFrequencyMatchesVolume(
    program: com.stratum.domain.program.generation.draft.ProgramDraft,
    context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
    scenarioLabel: String,
  ) {
    val report = validateProgramMetrics(program, context)
    for ((muscle, weekly) in report.summary.weeklyMuscleVolume) {
      if (weekly <= 10) continue
      val tier = frequencyTierForWeeklySets(weekly)
      val minFreq = when (tier) {
        FrequencyTier.One -> 1
        FrequencyTier.Two -> 2
        FrequencyTier.Three -> 3
      }
      val actual = report.summary.muscleWeeklyFrequency[muscle] ?: 0
      assertTrue(
        actual >= minFreq,
        "$scenarioLabel: $muscle has ${weekly.toInt()} weekly sets but only ${actual}× frequency (need ≥${minFreq}×)",
      )
    }
  }

  private fun primarySetsByMuscleInWorkout(
    workout: ProgramWorkoutDraft,
    context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
  ): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    for (lift in workout.workoutLifts.orEmpty()) {
      val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
      val setCount = lift.workoutLiftSets?.size ?: 0
      if (setCount == 0) continue
      for (muscle in meta.primaryMuscles) {
        totals[muscle] = (totals[muscle] ?: 0) + setCount
      }
    }
    return totals
  }

  companion object {
    @JvmStatic
    fun generationScenarios(): List<ScenarioConfig> = GENERATION_SCENARIOS

    @JvmStatic
    fun maintenanceDayCounts(): List<Int> = listOf(4, 5, 6)

    @JvmStatic
    fun dayCounts(): List<Int> = listOf(3, 4, 5, 6)
  }
}
