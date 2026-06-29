package com.stratum.core.data.seed

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [YuhonasImportMapper] — the pure mapping/classification logic extracted from
 * the offline yuhonas import script. These do not touch Room, assets, or Android; they verify
 * the deterministic transformations that produce the seed asset.
 *
 * The classifier and muscle mapper are the highest-risk logic in OS-4b: a wrong mapping silently
 * mis-categorizes hundreds of lifts. These tests pin down representative exercises spanning
 * every [MovementPattern] so a regression is caught before the asset is regenerated.
 */
class YuhonasImportMapperTest {

    @Nested
    @DisplayName("Muscle mapping")
    inner class MuscleMapping {

        @Test
        @DisplayName("Given each yuhonas muscle when mapped then it resolves to the correct MuscleGroup")
        fun mapsAllKnownMuscles() {
            assertEquals(MuscleGroup.Quad, YuhonasImportMapper.mapMuscle("quadriceps"))
            assertEquals(MuscleGroup.Hamstring, YuhonasImportMapper.mapMuscle("hamstrings"))
            assertEquals(MuscleGroup.Glute, YuhonasImportMapper.mapMuscle("glutes"))
            assertEquals(MuscleGroup.Calf, YuhonasImportMapper.mapMuscle("calves"))
            assertEquals(MuscleGroup.Chest, YuhonasImportMapper.mapMuscle("chest"))
            assertEquals(MuscleGroup.Bicep, YuhonasImportMapper.mapMuscle("biceps"))
            assertEquals(MuscleGroup.Tricep, YuhonasImportMapper.mapMuscle("triceps"))
            assertEquals(MuscleGroup.Forearm, YuhonasImportMapper.mapMuscle("forearms"))
            assertEquals(MuscleGroup.Ab, YuhonasImportMapper.mapMuscle("abdominals"))
            assertEquals(MuscleGroup.LowerBack, YuhonasImportMapper.mapMuscle("lower back"))
            assertEquals(MuscleGroup.Trap, YuhonasImportMapper.mapMuscle("traps"))
            assertEquals(MuscleGroup.Abductor, YuhonasImportMapper.mapMuscle("abductors"))
            assertEquals(MuscleGroup.Adductor, YuhonasImportMapper.mapMuscle("adductors"))
        }

        @Test
        @DisplayName("Given lats and middle back when mapped then both resolve to Back")
        fun conflatesBackMuscles() {
            assertEquals(MuscleGroup.Back, YuhonasImportMapper.mapMuscle("lats"))
            assertEquals(MuscleGroup.Back, YuhonasImportMapper.mapMuscle("middle back"))
        }

        @Test
        @DisplayName("Given shoulders when mapped then it defaults to AnteriorDeltoid")
        fun shouldersDefaultToAnteriorDeltoid() {
            assertEquals(MuscleGroup.AnteriorDeltoid, YuhonasImportMapper.mapMuscle("shoulders"))
        }

        @Test
        @DisplayName("Given neck when mapped then it is dropped (null)")
        fun dropsNeck() {
            assertNull(YuhonasImportMapper.mapMuscle("neck"))
        }

        @Test
        @DisplayName("Given an unknown muscle name when mapped then it is dropped (null)")
        fun dropsUnknownMuscle() {
            assertNull(YuhonasImportMapper.mapMuscle("tibialis"))
            assertNull(YuhonasImportMapper.mapMuscle("bogus"))
        }
    }

    @Nested
    @DisplayName("Shoulder refinement")
    inner class ShoulderRefinement {

        @Test
        @DisplayName("Given HorizontalPull pattern when refining shoulders then it resolves to PosteriorDeltoid")
        fun horizontalPullMapsToRearDelts() {
            assertEquals(
                MuscleGroup.PosteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.HorizontalPull),
            )
        }

        @Test
        @DisplayName("Given ShoulderAbduction pattern when refining shoulders then it resolves to LateralDeltoid")
        fun lateralDeltIsoMapsToSideDelts() {
            assertEquals(
                MuscleGroup.LateralDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.ShoulderAbduction),
            )
        }

        @Test
        @DisplayName("Given ShoulderHorizontalAbduction pattern when refining shoulders then it resolves to PosteriorDeltoid")
        fun rearDeltIsoMapsToRearDelts() {
            assertEquals(
                MuscleGroup.PosteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.ShoulderHorizontalAbduction),
            )
        }

        @Test
        @DisplayName("Given ShoulderFlexion pattern when refining shoulders then it resolves to AnteriorDeltoid")
        fun anteriorDeltIsoMapsToFrontDelts() {
            assertEquals(
                MuscleGroup.AnteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.ShoulderFlexion),
            )
        }

        @Test
        @DisplayName("Given a push pattern when refining shoulders then it resolves to AnteriorDeltoid")
        fun pushPatternsMapToFrontDelts() {
            assertEquals(
                MuscleGroup.AnteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.HorizontalPress),
            )
            assertEquals(
                MuscleGroup.AnteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.VerticalPress),
            )
            assertEquals(
                MuscleGroup.AnteriorDeltoid,
                YuhonasImportMapper.mapShoulderMuscle(MovementPattern.InclinePress),
            )
        }
    }

    @Nested
    @DisplayName("MovementPattern classification — name-based")
    inner class NameBasedClassification {

        @Test
        @DisplayName("Given a lateral raise exercise when classified then it resolves to ShoulderAbduction")
        fun lateralRaise() {
            assertEquals(
                MovementPattern.ShoulderAbduction,
                classify("Dumbbell Lateral Raise", "push", "isolation", listOf("shoulders")),
            )
        }

        @Test
        @DisplayName("Given a shrug exercise when classified then it resolves to ScapularElevation")
        fun shrug() {
            assertEquals(
                MovementPattern.ScapularElevation,
                classify("Barbell Shrug", "pull", "isolation", listOf("traps")),
            )
        }

        @Test
        @DisplayName("Given a wrist curl exercise when classified then it resolves to WristFlexionExtension")
        fun wristCurl() {
            assertEquals(
                MovementPattern.WristFlexionExtension,
                classify("Wrist Curl", "pull", "isolation", listOf("forearms")),
            )
        }

        @Test
        @DisplayName("Given a crunch exercise when classified then it resolves to SpinalFlexion")
        fun crunch() {
            assertEquals(
                MovementPattern.SpinalFlexion,
                classify("Crunch", "pull", "isolation", listOf("abdominals")),
            )
        }

        @Test
        @DisplayName("Given a calf raise exercise when classified then it resolves to PlantarFlexion")
        fun calfRaise() {
            assertEquals(
                MovementPattern.PlantarFlexion,
                classify("Standing Calf Raises", "push", "isolation", listOf("calves")),
            )
        }

        @Test
        @DisplayName("Given a hip thrust when classified then it resolves to HipExtension")
        fun hipThrust() {
            assertEquals(
                MovementPattern.HipExtension,
                classify("Barbell Hip Thrust", "push", "compound", listOf("glutes")),
            )
        }

        @Test
        @DisplayName("Given a leg extension when classified then it resolves to KneeExtension")
        fun legExtension() {
            assertEquals(
                MovementPattern.KneeExtension,
                classify("Leg Extensions", "push", "isolation", listOf("quadriceps")),
            )
        }

        @Test
        @DisplayName("Given a leg curl when classified then it resolves to KneeFlexion")
        fun legCurl() {
            assertEquals(
                MovementPattern.KneeFlexion,
                classify("Lying Leg Curls", "pull", "isolation", listOf("hamstrings")),
            )
        }
    }

    @Nested
    @DisplayName("MovementPattern classification — pattern-based")
    inner class PatternBasedClassification {

        @Test
        @DisplayName("Given a barbell squat when classified then it resolves to SquatPattern")
        fun squat() {
            assertEquals(
                MovementPattern.SquatPattern,
                classify("Barbell Full Squat", "push", "compound", listOf("quadriceps")),
            )
        }

        @Test
        @DisplayName("Given a bench press when classified then it resolves to HorizontalPress")
        fun benchPress() {
            assertEquals(
                MovementPattern.HorizontalPress,
                classify("Barbell Bench Press - Medium Grip", "push", "compound", listOf("chest")),
            )
        }

        @Test
        @DisplayName("Given an incline bench press when classified then it resolves to InclinePress")
        fun inclinePress() {
            assertEquals(
                MovementPattern.InclinePress,
                classify("Incline Dumbbell Press", "push", "compound", listOf("chest")),
            )
        }

        @Test
        @DisplayName("Given an overhead press when classified then it resolves to VerticalPress")
        fun overheadPress() {
            assertEquals(
                MovementPattern.VerticalPress,
                classify("Standing Military Press", "push", "compound", listOf("shoulders")),
            )
        }

        @Test
        @DisplayName("Given a pull-up when classified then it resolves to VerticalPull")
        fun pullUp() {
            assertEquals(
                MovementPattern.VerticalPull,
                classify("Pull-Up", "pull", "compound", listOf("lats")),
            )
        }

        @Test
        @DisplayName("Given a barbell row when classified then it resolves to HorizontalPull")
        fun barbellRow() {
            assertEquals(
                MovementPattern.HorizontalPull,
                classify("Bent Over Barbell Row", "pull", "compound", listOf("middle back")),
            )
        }

        @Test
        @DisplayName("Given a deadlift when classified then it resolves to HipHinge")
        fun deadlift() {
            assertEquals(
                MovementPattern.HipHinge,
                classify("Barbell Deadlift", "pull", "compound", listOf("hamstrings")),
            )
        }
    }

    @Nested
    @DisplayName("MovementPattern classification — isolation fallback")
    inner class IsolationFallback {

        @Test
        @DisplayName("Given a bicep curl not name-matched when classified then it resolves to ElbowFlexion")
        fun bicepIsolation() {
            assertEquals(
                MovementPattern.ElbowFlexion,
                classify("Dumbbell Bicep Curl", "pull", "isolation", listOf("biceps")),
            )
        }

        @Test
        @DisplayName("Given a tricep pushdown not name-matched when classified then it resolves to ElbowExtension")
        fun tricepIsolation() {
            assertEquals(
                MovementPattern.ElbowExtension,
                classify("Triceps Pushdown", "push", "isolation", listOf("triceps")),
            )
        }

        @Test
        @DisplayName("Given a cable fly when classified then it resolves to ShoulderAdduction")
        fun chestIsolation() {
            assertEquals(
                MovementPattern.ShoulderAdduction,
                classify("Cable Flyes", "pull", "isolation", listOf("chest")),
            )
        }

        @Test
        @DisplayName("Given an abductor machine exercise when classified then it resolves to HipExtension")
        fun abductorMachine() {
            assertEquals(
                MovementPattern.HipExtension,
                classify("Machine Hip Abduction", "push", "isolation", listOf("abductors")),
            )
        }

        @Test
        @DisplayName("Given an adductor machine exercise when classified then it resolves to HipExtension")
        fun adductorMachine() {
            assertEquals(
                MovementPattern.HipExtension,
                classify("Machine Hip Adduction", "push", "isolation", listOf("adductors")),
            )
        }
    }

    @Nested
    @DisplayName("allowWeightChangeBetweenSets heuristic")
    inner class AllowWeightChangeHeuristic {

        @Test
        @DisplayName("Given an isolation exercise when derived then it allows weight change")
        fun isolationAllowsChange() {
            assertTrue(YuhonasImportMapper.allowWeightChangeBetweenSets("isolation", "dumbbell"))
            assertTrue(YuhonasImportMapper.allowWeightChangeBetweenSets("isolation", "cable"))
            assertTrue(YuhonasImportMapper.allowWeightChangeBetweenSets("isolation", null))
        }

        @Test
        @DisplayName("Given a barbell compound when derived then it does not allow weight change")
        fun barbellCompoundHoldsWeight() {
            assertEquals(
                false,
                YuhonasImportMapper.allowWeightChangeBetweenSets("compound", "barbell"),
            )
        }

        @Test
        @DisplayName("Given a bodyweight compound when derived then it does not allow weight change")
        fun bodyweightCompoundHoldsWeight() {
            assertEquals(
                false,
                YuhonasImportMapper.allowWeightChangeBetweenSets("compound", "body only"),
            )
        }

        @Test
        @DisplayName("Given a dumbbell compound when derived then it allows weight change")
        fun dumbbellCompoundAllowsChange() {
            assertTrue(YuhonasImportMapper.allowWeightChangeBetweenSets("compound", "dumbbell"))
        }

        @Test
        @DisplayName("Given a null mechanic when derived then it does not allow weight change")
        fun nullMechanicHoldsWeight() {
            assertEquals(
                false,
                YuhonasImportMapper.allowWeightChangeBetweenSets(null, "barbell"),
            )
        }
    }

    @Nested
    @DisplayName("Preset tag heuristic")
    inner class PresetTagHeuristic {

        @Test
        @DisplayName("Given cable equipment when derived then it is CommercialGym only")
        fun cableIsCommercialOnly() {
            assertEquals(
                listOf("CommercialGym"),
                YuhonasImportMapper.presetTags("cable"),
            )
        }

        @Test
        @DisplayName("Given machine equipment when derived then it is CommercialGym only")
        fun machineIsCommercialOnly() {
            assertEquals(
                listOf("CommercialGym"),
                YuhonasImportMapper.presetTags("machine"),
            )
        }

        @Test
        @DisplayName("Given barbell equipment when derived then it is both presets")
        fun barbellIsBoth() {
            assertEquals(
                listOf("CommercialGym", "GarageGym"),
                YuhonasImportMapper.presetTags("barbell"),
            )
        }

        @Test
        @DisplayName("Given body-only equipment when derived then it is both presets")
        fun bodyOnlyIsBoth() {
            assertEquals(
                listOf("CommercialGym", "GarageGym"),
                YuhonasImportMapper.presetTags("body only"),
            )
        }

        @Test
        @DisplayName("Given null equipment when derived then it is both presets")
        fun nullIsBoth() {
            assertEquals(
                listOf("CommercialGym", "GarageGym"),
                YuhonasImportMapper.presetTags(null),
            )
        }
    }

    @Nested
    @DisplayName("Slug-ification")
    inner class Slugification {

        @Test
        @DisplayName("Given a PascalCase underscored id when slugified then it becomes kebab-case")
        fun slugifiesId() {
            assertEquals("barbell-full-squat", YuhonasImportMapper.toSlugId("Barbell_Full_Squat"))
            assertEquals("barbell-curl", YuhonasImportMapper.toSlugId("Barbell_Curl"))
            assertEquals("pull-up", YuhonasImportMapper.toSlugId("Pull-Up"))
        }

        @Test
        @DisplayName("Given an id with doubled dashes when slugified then they are collapsed")
        fun collapsesDoubledDashes() {
            assertEquals("barbell-bench-press-medium-grip", YuhonasImportMapper.toSlugId("Barbell_Bench_Press_-_Medium_Grip"))
        }
    }

    // --- Helper -------------------------------------------------------------

    private fun classify(
        name: String,
        force: String?,
        mechanic: String?,
        primaryMuscles: List<String>,
    ): MovementPattern = YuhonasImportMapper.classifyPattern(name, force, mechanic, primaryMuscles)
}