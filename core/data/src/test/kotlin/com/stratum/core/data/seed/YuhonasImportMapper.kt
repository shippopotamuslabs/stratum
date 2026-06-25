package com.stratum.core.data.seed

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Pure mapping functions that transform a yuhonas/free-exercise-db exercise record into a
 * Stratum [LiftSeedDto]. Lives in the test source set — the runtime seeder reads the
 * pre-generated `seed-lifts.json` asset and never calls these functions.
 *
 * **Re-importing:** if the yuhonas dataset is ever refreshed and you want to regenerate the
 * seed asset, write a temporary `main()` that reads `dist/exercises.json`, calls
 * [classifyPattern] / [mapMuscle] / [allowWeightChangeBetweenSets] / [presetTags] /
 * [toSlugId] for each exercise, and writes the result as `seed-lifts.json`. The 44 unit tests
 * in [YuhonasImportMapperTest] pin the classification rules so changes are verified first.
 *
 * The yuhonas dataset (`dist/exercises.json`) uses a 17-value muscle vocabulary, a
 * push/pull/static `force`, a compound/isolation `mechanic`, and a `category`. Stratum uses a
 * 17-value [MuscleGroup] (neck dropped) and an 18-value [MovementPattern] that combines
 * force + mechanic + movement plane. These functions bridge that gap deterministically.
 *
 * **Lossy mappings** (intentional):
 * - yuhonas `lats` and `middle back` both → [MuscleGroup.Back] (user decision: a single Back
 *   group is sufficient; the vertical/horizontal pull split distinguishes them in practice).
 * - yuhonas `shoulders` → [MuscleGroup.AnteriorDeltoid] by default, refined by movement pattern
 *   (see [mapShoulderMuscle]).
 * - yuhonas `neck` is dropped (no Stratum counterpart).
 *
 * **Heuristics** (not from the source data):
 * - [MovementPattern] is classified from force + mechanic + name + primary muscle, because
 *   yuhonas has no movement-plane concept.
 * - [LiftSeedDto.allowWeightChangeBetweenSets] is derived from mechanic + equipment.
 * - [LiftSeedDto.presetTags] are derived from equipment (CommercialGym vs GarageGym).
 *
 * These heuristics are **not** embedded in the runtime seeder — they run once, offline, when
 * regenerating the seed asset. The asset carries the resolved values as plain data.
 */
object YuhonasImportMapper {

    /** yuhonas categories imported for Stratum v1 (bodybuilding-only; cardio/stretching/plyometrics/olympic/strongman skipped). */
    val IMPORTED_CATEGORIES: Set<String> = setOf(
        "strength",
        "powerlifting",
    )

    // --- Muscle mapping -----------------------------------------------------

    /**
     * Maps a yuhonas muscle name to a [MuscleGroup], or null if there is no Stratum counterpart
     * (only `neck` is dropped). `shoulders` maps to [MuscleGroup.AnteriorDeltoid] as the default
     * head; use [mapShoulderMuscle] when the movement pattern is known for a refined mapping.
     */
    fun mapMuscle(yuhonasMuscle: String): MuscleGroup? = when (yuhonasMuscle) {
        "quadriceps" -> MuscleGroup.Quad
        "hamstrings" -> MuscleGroup.Hamstring
        "glutes" -> MuscleGroup.Glute
        "calves" -> MuscleGroup.Calf
        "chest" -> MuscleGroup.Chest
        "biceps" -> MuscleGroup.Bicep
        "triceps" -> MuscleGroup.Tricep
        "forearms" -> MuscleGroup.Forearm
        "abdominals" -> MuscleGroup.Ab
        "lats" -> MuscleGroup.Back
        "middle back" -> MuscleGroup.Back
        "lower back" -> MuscleGroup.LowerBack
        "traps" -> MuscleGroup.Trap
        "abductors" -> MuscleGroup.Abductor
        "adductors" -> MuscleGroup.Adductor
        "shoulders" -> MuscleGroup.AnteriorDeltoid
        "neck" -> null
        else -> null
    }

    /**
     * Refines the `shoulders` → deltoid-head mapping based on the classified [MovementPattern].
     * Called for each `shoulders` entry in the muscle list after [classifyPattern] has resolved.
     *
     * - Push patterns → [MuscleGroup.AnteriorDeltoid] (front delts drive pressing).
     * - [MovementPattern.HorizontalPull] → [MuscleGroup.PosteriorDeltoid] (rows hit rear delts).
     * - [MovementPattern.DeltIso] → [MuscleGroup.LateralDeltoid] (lateral raises isolate the
     *   side head — the classifier routes "lateral raise" names here).
     * - Otherwise → [MuscleGroup.AnteriorDeltoid] (default).
     */
    fun mapShoulderMuscle(pattern: MovementPattern): MuscleGroup = when (pattern) {
        MovementPattern.HorizontalPull -> MuscleGroup.PosteriorDeltoid
        MovementPattern.DeltIso -> MuscleGroup.LateralDeltoid
        else -> MuscleGroup.AnteriorDeltoid
    }

    // --- MovementPattern classification ------------------------------------

    /**
     * Classifies a yuhonas exercise into a Stratum [MovementPattern]. Rules are applied in
     * priority order: name-based first (highest confidence), then force+mechanic+muscle pattern
     * matching, then isolation/compound fallbacks. See the plan (OS-4b §5) for the full table.
     *
     * @param name the exercise name (e.g. "Barbell Full Squat").
     * @param force "push", "pull", "static", or null.
     * @param mechanic "compound", "isolation", or null.
     * @param primaryMuscles the yuhonas primary muscle names (e.g. ["quadriceps"]).
     */
    fun classifyPattern(
        name: String,
        force: String?,
        mechanic: String?,
        primaryMuscles: List<String>,
    ): MovementPattern {
        val lowerName = name.lowercase()

        // 1. Name-based (highest confidence — explicit movement in the name).
        nameBasedPattern(lowerName)?.let { return it }

        val primary = primaryMuscles.mapNotNull { mapMuscle(it) }
        val firstPrimary = primary.firstOrNull()

        // 2. Pattern-based (force + mechanic + plane inference from name).
        compoundPattern(lowerName, force, mechanic, firstPrimary)?.let { return it }

        // 3. Isolation fallback.
        if (mechanic == "isolation") {
            return isolationPattern(firstPrimary) ?: defaultCompoundOrLegPush(force, firstPrimary)
        }

        // 4. Compound fallback.
        return defaultCompoundOrLegPush(force, firstPrimary)
    }

    /** Name-based classification — returns a pattern if the exercise name is explicit. */
    @Suppress("CyclomaticComplexMethod")
    private fun nameBasedPattern(lowerName: String): MovementPattern? = when {
        // --- Direct isolation movements by name ---
        lowerName.contains("lateral raise") || lowerName.contains("side raise") -> MovementPattern.DeltIso
        lowerName.contains("shrug") -> MovementPattern.TrapIso
        containsAny(lowerName, "wrist", "forearm curl", "reverse curl") -> MovementPattern.ForearmIso
        containsAny(lowerName, "crunch", "sit-up", "hanging leg raise") -> MovementPattern.AbIso
        lowerName.contains("calf raise") -> MovementPattern.Calves
        containsAny(lowerName, "hip thrust", "glute bridge", "glute kickback", "hip lift") -> MovementPattern.GluteIso
        lowerName.contains("leg extension") -> MovementPattern.QuadIso
        containsAny(lowerName, "leg curl", "hamstring curl") -> MovementPattern.HamstringIso
        // Curl variants not caught by the simple "curl" substring (drag curl, high cable curl).
        lowerName.contains("curl") -> MovementPattern.BicepIso
        // Fly / pullover / pecdeck variants are chest isolation.
        containsAny(lowerName, "fly", "flye", "pullover", "pect deck", "pec deck") -> MovementPattern.ChestIso
        // Tricep isolation by name.
        containsAny(lowerName, "tricep", "triceps", "skull crusher", "pushdown", "kickback") -> MovementPattern.TricepIso

        // --- Hip hinge / posterior chain by name ---
        // yuhonas often tags good mornings, deadlifts, and RDLs with atypical primary muscles
        // (hamstrings, lower back, or even quadriceps). The name is more reliable than the muscle.
        containsAny(
            lowerName,
            "good morning", "good-morning", "romanian", "rdl", "stiff leg", "stiff-leg",
            "rack pull", "rack-pull", "pull through", "pull-through", "hyperextension",
            "glute ham raise", "back extension",
        ) -> MovementPattern.HipHinge
        lowerName.contains("deadlift") -> MovementPattern.HipHinge

        // --- Olympic lifts (always HipHinge — pull-dominant explosive movements) ---
        containsAny(
            lowerName,
            "clean", "snatch", "jerk", "power clean", "power snatch", "hang clean", "hang snatch",
            "clean and jerk", "clean and press", "muscle snatch",
        ) -> MovementPattern.HipHinge

        // --- Ab/core compound by name (yuhonas tags these with ab primary but compound mechanic) ---
        containsAny(
            lowerName,
            "ab rollout", "ab-roller", "ab roller", "rollout", "russian twist", "wood chop",
            "woodchop", "pallof press", "leg pull-in", "pull-in", "flutter kick", "dead bug",
            "plank", "cocoons", "butt-up", "elbow to knee", "jackknife", "spell caster",
            "spider crawl", "judo flip", "slam", "air bike", "wind sprint",
            "kettlebell pass", "kettlebell windmill", "hip raise", "pike", "bottoms up",
            "otis-up", "otis up", "plate twist", "landmine 180", "cable lift",
        ) -> MovementPattern.AbIso

        // --- Lunge / step-up (LegPush — they are squat-pattern derivatives) ---
        containsAny(lowerName, "lunge", "step up", "step-up") -> MovementPattern.LegPush

        // --- Dips (triceps compound — classify by name) ---
        lowerName.contains("dip") -> MovementPattern.TricepIso

        // --- Push-up variants (HorizontalPush) ---
        lowerName.contains("push-up") || lowerName.contains("pushup") -> MovementPattern.HorizontalPush

        // --- Bench / floor / board / pin / rack press variants ---
        // yuhonas sometimes tags close-grip or powerlifting bench with primary=triceps, which
        // would route them to the wrong fallback. Any "bench press" or "floor press" is a
        // horizontal push (or incline/decline variant).
        containsAny(lowerName, "bench press", "floor press", "board press", "pin press") -> {
            benchPressPattern(lowerName)
        }
        // Close-grip / JM press variants are horizontal pushing (tricep-focused bench).
        containsAny(lowerName, "close-grip", "close grip", "jm press", "neck press") -> MovementPattern.HorizontalPush
        // Decline press (smith or barbell) is horizontal.
        lowerName.contains("decline") && lowerName.contains("press") -> MovementPattern.HorizontalPush
        lowerName.contains("svend press") || lowerName.contains("chain press") -> MovementPattern.ChestIso

        // --- Overhead / vertical press by name (shoulders-primary compounds) ---
        // These have `shoulders` primary in yuhonas; the name confirms vertical pressing.
        containsAny(
            lowerName,
            "arnold", "bent press", "bradford", "seesaw press", "side press", "para press",
            "push press", "circus bell", "log lift", "thruster",
        ) -> MovementPattern.VerticalPush

        // --- Glute / hip isolation by name ---
        containsAny(
            lowerName,
            "hip extension", "hip bridge", "hip lift", "monster walk", "hip flexion",
        ) -> MovementPattern.GluteIso

        // --- Face pulls (HorizontalPull — rear delt focus) ---
        lowerName.contains("face pull") -> MovementPattern.HorizontalPull

        // --- Kettlebell sumo high pull (upright row variant — TrapIso) ---
        lowerName.contains("sumo high pull") -> MovementPattern.TrapIso

        // --- Iron cross (lateral raise hold — DeltIso) ---
        lowerName.contains("iron cross") -> MovementPattern.DeltIso

        // --- Front raise (DeltIso) ---
        lowerName.contains("front raise") || lowerName.contains("dumbbell raise") -> MovementPattern.DeltIso

        // --- Carries / sled drags (forearm/trap dominant, but LegPush is the closest slot) ---
        containsAny(lowerName, "farmer", "rickshaw", "yoke", "sled drag", "sled push") -> MovementPattern.LegPush

        // --- Rotator cuff / shoulder rehab ---
        containsAny(lowerName, "internal rotation", "external rotation") -> MovementPattern.DeltIso

        // --- Strongman lifts (hip-dominant pulls) ---
        containsAny(lowerName, "tire flip", "sandbag load", "backward drag", "conan") -> MovementPattern.HipHinge

        // --- Battling ropes (conditioning — AbIso is closest) ---
        lowerName.contains("battling rope") -> MovementPattern.AbIso

        // --- Kettlebell pirate ships (lateral raise variant — DeltIso) ---
        lowerName.contains("pirate ship") -> MovementPattern.DeltIso

        // --- Rack delivery (strongman overhead — VerticalPush) ---
        lowerName.contains("rack delivery") -> MovementPattern.VerticalPush

        // --- Balance board (calve stability — Calves) ---
        lowerName.contains("balance board") -> MovementPattern.Calves

        else -> null
    }

    /**
     * Resolves a bench-press-family exercise to its plane. "Incline" → [InclinePush];
     * "decline" → [HorizontalPush] (decline is still a horizontal plane press); otherwise
     * [HorizontalPush].
     */
    private fun benchPressPattern(lowerName: String): MovementPattern = when {
        lowerName.contains("incline") -> MovementPattern.InclinePush
        else -> MovementPattern.HorizontalPush
    }

    /** Compound-movement classification — returns a pattern if force+mechanic+muscle matches. */
    private fun compoundPattern(
        lowerName: String,
        force: String?,
        mechanic: String?,
        firstPrimary: MuscleGroup?,
    ): MovementPattern? {
        if (mechanic != "compound") return null

        return when {
            force == "pull" && firstPrimary == MuscleGroup.Back -> when {
                containsAny(lowerName, "pull-up", "chin-up", "pulldown", "muscle up", "rope climb") -> MovementPattern.VerticalPull
                lowerName.contains("row") -> MovementPattern.HorizontalPull
                else -> MovementPattern.VerticalPull
            }
            force == "push" && firstPrimary == MuscleGroup.Chest -> when {
                lowerName.contains("incline") -> MovementPattern.InclinePush
                containsAny(lowerName, "overhead", "military", "shoulder press") -> MovementPattern.VerticalPush
                else -> MovementPattern.HorizontalPush
            }
            // Overhead / military / shoulder presses have `shoulders` (→ AnteriorDeltoid) as
            // primary, not Chest — handle them here, after the chest branch.
            force == "push" && firstPrimary == MuscleGroup.AnteriorDeltoid ->
                if (containsAny(lowerName, "overhead", "military", "shoulder press", "press")) {
                    MovementPattern.VerticalPush
                } else {
                    MovementPattern.HorizontalPush
                }
            force == "push" && firstPrimary == MuscleGroup.Quad ->
                if (containsAny(lowerName, "deadlift", "rdl", "romanian")) {
                    MovementPattern.HipHinge
                } else {
                    MovementPattern.LegPush
                }
            // Good mornings and rack pulls are tagged with hamstring/lower-back primary but
            // are hip hinges — the name rules in nameBasedPattern catch most, but some land here.
            force == "pull" && firstPrimary == MuscleGroup.Hamstring -> MovementPattern.HipHinge
            force == "pull" && firstPrimary == MuscleGroup.LowerBack -> MovementPattern.HipHinge
            force == "static" && firstPrimary == MuscleGroup.Ab -> MovementPattern.AbIso
            else -> null
        }
    }

    /** Isolation-movement fallback by primary muscle. */
    private fun isolationPattern(firstPrimary: MuscleGroup?): MovementPattern? = when (firstPrimary) {
        MuscleGroup.Bicep -> MovementPattern.BicepIso
        MuscleGroup.Tricep -> MovementPattern.TricepIso
        MuscleGroup.Chest -> MovementPattern.ChestIso
        MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid, MuscleGroup.PosteriorDeltoid -> MovementPattern.DeltIso
        MuscleGroup.Trap -> MovementPattern.TrapIso
        MuscleGroup.Glute -> MovementPattern.GluteIso
        MuscleGroup.Abductor, MuscleGroup.Adductor -> MovementPattern.GluteIso
        else -> null
    }

    /** True if [lowerName] contains any of [keywords]. */
    private fun containsAny(lowerName: String, vararg keywords: String): Boolean =
        keywords.any { lowerName.contains(it) }

    private fun defaultCompoundOrLegPush(force: String?, firstPrimary: MuscleGroup?): MovementPattern = when {
        force == "push" && firstPrimary == MuscleGroup.Quad -> MovementPattern.LegPush
        force == "pull" && firstPrimary == MuscleGroup.Back -> MovementPattern.VerticalPull
        force == "push" && firstPrimary == MuscleGroup.Chest -> MovementPattern.HorizontalPush
        force == "pull" && firstPrimary == MuscleGroup.Hamstring -> MovementPattern.HipHinge
        else -> MovementPattern.LegPush
    }

    // --- Heuristics ---------------------------------------------------------

    /**
     * Derives `allowWeightChangeBetweenSets` from mechanic + equipment. Isolation lifts change
     * weight every set; barbell/bodyweight compounds hold weight; dumbbell/cable/machine
     * compounds allow pin/plate changes between sets. See plan OS-4b §6.
     */
    fun allowWeightChangeBetweenSets(mechanic: String?, equipment: String?): Boolean {
        if (mechanic == "isolation") return true
        if (mechanic == "compound") {
            return equipment !in FIXED_EQUIPMENT
        }
        return false
    }

    private val FIXED_EQUIPMENT: Set<String> = setOf("barbell", "e-z curl bar", "body only")

    /**
     * Derives preset tags from equipment. Cable/machine/specialty equipment is CommercialGym-only
     * (a garage gym rarely has a cable column or machine stack). Free-weight and bodyweight lifts
     * are available in both presets. See plan OS-4b §7.
     */
    fun presetTags(equipment: String?): List<String> =
        if (equipment in COMMERCIAL_ONLY_EQUIPMENT) listOf("CommercialGym")
        else listOf("CommercialGym", "GarageGym")

    private val COMMERCIAL_ONLY_EQUIPMENT: Set<String> = setOf(
        "cable", "machine", "exercise ball", "foam roll", "medicine ball",
    )

    /**
     * Converts a yuhonas id slug (`Barbell_Full_Squat`) to a kebab-case Stratum id
     * (`barbell-full-squat`): lowercase, `_` → `-`, strip non-alphanumeric chars except dashes,
     * collapse doubled dashes. The original id is preserved as `imageSlug` for R2 image URL
     * construction.
     */
    fun toSlugId(yuhonasId: String): String =
        yuhonasId.lowercase()
            .replace('_', '-')
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-+"), "-")
}
