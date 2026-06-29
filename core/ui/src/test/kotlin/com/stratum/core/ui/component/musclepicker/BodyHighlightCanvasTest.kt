package com.stratum.core.ui.component.musclepicker

import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BodyHighlightCanvasTest {
    @Test
    @DisplayName("UI-BH-401 triceps rear delts and calves prefer back view")
    fun uiBh401_backViewPreferredMusclesUseBackSilhouette() {
        assertEquals(BodyView.Back, bodyViewForMuscle(MuscleGroup.Tricep))
        assertEquals(BodyView.Back, bodyViewForMuscle(MuscleGroup.PosteriorDeltoid))
        assertEquals(BodyView.Back, bodyViewForMuscle(MuscleGroup.Calf))
    }

    @Test
    @DisplayName("UI-BH-402 chest and quads stay on front view")
    fun uiBh402_frontOnlyMusclesStayOnFrontSilhouette() {
        assertEquals(BodyView.Front, bodyViewForMuscle(MuscleGroup.Chest))
        assertEquals(BodyView.Front, bodyViewForMuscle(MuscleGroup.Quad))
    }
}
