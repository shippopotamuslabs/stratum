package com.stratum.domain.program.generation.slots

/**
 * Organic session role — how a workout is *composed*, independent of catalog split templates.
 *
 * Used by [slotsForSessionType] to specialize slot lists (e.g. spec-focused upper days).
 *
 * Universe construction no longer loops session types; [UpperBalanced] / [LowerPrimary] remain
 * as domain-shell compositions inside [crossDomainTouches] for touch-budget fidelity.
 * Other values are for [slotsForSessionType] / explicit composition overrides.
 */
enum class SessionComposition {
    /** Maintenance-oriented upper: compounds + light iso. Used as universe touch domain shell. */
    UpperBalanced,

    /**
     * Dedicated specialization upper: extra direct work for specialized upper muscles.
     *
     * Retained for [slotsForSessionType] / cross-domain touch override paths.
     */
    UpperSpecFocused,

    /**
     * Third upper in a 5-day week: finishes remaining chest/back/shoulder volume.
     *
     * No production caller currently assigns this value.
     */
    UpperVolumeFinisher,

    /** Primary lower: squat/hinge anchors and in-domain leg iso. Used as universe touch domain shell. */
    LowerPrimary,

    /**
     * Lower with supplementary upper-domain touches (arms/rear delt on leg day).
     *
     * Retained for [slotsForSessionType] / cross-domain touch override paths.
     */
    LowerWithUpperTouch,
}
