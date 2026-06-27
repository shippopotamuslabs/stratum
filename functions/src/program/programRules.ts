/**
 * Deterministic program-generation rules (OS-7 D2). The server post-pass uses these to overwrite
 * the model's derived numerics so the LLM owns structure (lift selection, split, set/rep shape) and
 * deterministic code owns every numeric rule (progression scheme, RIR, straight sets).
 *
 * Normative source: docs/onboarding-vertical-slices.md §2.3. Any change here must be reflected in
 * the matching test_spec scenarios (PROG-GEN-703/704/705).
 */

/** Movement patterns classified as major compound for back-off RIR clamping (§2.3). */
export const MAJOR_COMPOUND_PATTERNS: ReadonlySet<string> = new Set([
  "LegPush",
  "HipHinge",
  "HorizontalPush",
  "VerticalPush",
  "InclinePush",
  "HorizontalPull",
  "VerticalPull",
]);

/** Top-set RIR goal on a Double-progression lift (fixed; §2.3). */
export const TOP_SET_RIR = 2;

/**
 * Assigns the progression scheme from training age. `Beginner -> Linear`, everything else `Double`.
 * This is the SOLE authority for `progressionScheme` — the model never authors it.
 */
export function schemeForTrainingAge(trainingAge: string): "Linear" | "Double" {
  return trainingAge === "Beginner" ? "Linear" : "Double";
}

/**
 * Clamps a proposed back-off RIR to the §2.3 lift-class table: major compounds 1–2 (default 2 when
 * out of range); everything else 0.
 */
export function clampBackOffRir(isMajorCompound: boolean, proposed: number): number {
  if (!isMajorCompound) {
    return 0;
  }
  // Major compound: 1..2; default 2 when the proposal is outside the range.
  if (proposed >= 1 && proposed <= 2) {
    return proposed;
  }
  return 2;
}
