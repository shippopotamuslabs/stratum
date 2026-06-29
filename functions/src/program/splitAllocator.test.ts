import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets } from "./volumeTargetPlanner";
import {
  allocateDayPlans,
  splitTemplateFor,
  type DayPlan,
} from "./splitAllocator";

/**
 * A rich enough lift catalog to satisfy upper, lower, push, pull, legs, and arms days.
 * Every major muscle has at least one enabled lift pattern so the feasibility guard never
 * strips a slot in the happy path.
 */
const LIFT_CATALOG = [
  // Chest
  { id: "bench", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"] },
  { id: "inc-press", movementPattern: "InclinePress", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
  { id: "fly", movementPattern: "ShoulderAdduction", primaryMuscles: ["Chest"], secondaryMuscles: [] },
  // Back
  { id: "row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep", "PosteriorDeltoid"] },
  { id: "pullup", movementPattern: "VerticalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  // Quads
  { id: "squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "leg-ext", movementPattern: "KneeExtension", primaryMuscles: ["Quad"], secondaryMuscles: [] },
  // Hamstrings
  { id: "rdl", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute", "LowerBack"] },
  { id: "leg-curl", movementPattern: "KneeFlexion", primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  // Glutes
  { id: "hip-thrust", movementPattern: "HipExtension", primaryMuscles: ["Glute"], secondaryMuscles: [] },
  // PlantarFlexion
  { id: "calf-raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  // Shoulders
  { id: "lat-raise", movementPattern: "ShoulderAbduction", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "rear-delt-fly", movementPattern: "ShoulderHorizontalAbduction", primaryMuscles: ["PosteriorDeltoid"], secondaryMuscles: [] },
  { id: "ohp", movementPattern: "VerticalPress", primaryMuscles: ["AnteriorDeltoid"], secondaryMuscles: ["Tricep"] },
  // Arms
  { id: "curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  // Abs
  { id: "crunch", movementPattern: "SpinalFlexion", primaryMuscles: ["Ab"], secondaryMuscles: [] },
];

const LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Glute", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "PosteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "AnteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Ab", estimatedMev: 2, estimatedMav: 6, estimatedMrv: 12, estimatedPv: 4 },
];

function contextFor(days: number, opts: { specialization?: Record<string, string>; lastTrained?: string } = {}) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"].slice(0, days).join(",");
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Intermediate",
        sessionLengthMinutes: 75,
        weekdays,
        trainingDaysPerWeek: days,
        ...(opts.lastTrained ? { lastTrained: opts.lastTrained } : {}),
      },
      specialization: opts.specialization ?? { Chest: "Specialize" },
      provenLifts: LIFT_CATALOG,
      availableLifts: [],
      literatureVolumeLandmarks: LANDMARKS,
    }),
  );
}

/** Sum a muscle's per-day `sets` across all day plans. */
function weeklySumFor(plans: DayPlan[], muscle: string): number {
  return plans.reduce((sum, plan) => {
    const budget = plan.muscleBudgets.find((b) => b.muscle === muscle);
    return sum + (budget?.sets ?? 0);
  }, 0);
}

/** How many days a muscle appears as a primary focus (has a muscleBudget). */
function frequencyFor(plans: DayPlan[], muscle: string): number {
  return plans.filter((p) => p.muscleBudgets.some((b) => b.muscle === muscle)).length;
}

describe("splitAllocator", () => {
  describe("splitTemplateFor", () => {
    it("returns a template for 2–6 days and null for unsupported counts", () => {
      assert.ok(splitTemplateFor(2)?.length === 2);
      assert.ok(splitTemplateFor(3)?.length === 3);
      assert.ok(splitTemplateFor(4)?.length === 4);
      assert.ok(splitTemplateFor(5)?.length === 5);
      assert.ok(splitTemplateFor(6)?.length === 6);
      assert.equal(splitTemplateFor(7), null);
      assert.equal(splitTemplateFor(1), null);
    });
  });

  describe("allocateDayPlans", () => {
    it("derives anatomically-correct suggested patterns (not catalog-order-dependent)", () => {
      // The core regression: an Upper day must suggest HorizontalPress/VerticalPull/arm iso/lateral
      // delt, and a Lower day must suggest SquatPattern (NOT HipHinge-for-quad or SpinalFlexion-for-hamstring).
      const ctx = contextFor(4);
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upper = plans.find((p) => p.theme.startsWith("Upper"))!;
      const upperPatterns = new Set(upper.suggestedPatterns.map((s) => s.pattern));
      assert.ok(upperPatterns.has("HorizontalPress"), "Upper should suggest a horizontal push");
      assert.ok(upperPatterns.has("VerticalPull"), "Upper should suggest a vertical pull");
      assert.ok(upperPatterns.has("ElbowExtension"), "Upper should suggest a tricep iso");
      assert.ok(upperPatterns.has("ElbowFlexion"), "Upper should suggest a bicep iso");

      const lower = plans.find((p) => p.theme.startsWith("Lower"))!;
      const lowerPatterns = new Set(lower.suggestedPatterns.map((s) => s.pattern));
      assert.ok(lowerPatterns.has("SquatPattern"), "Lower should suggest SquatPattern (squats), not HipHinge-for-quad");
      assert.ok(lowerPatterns.has("KneeFlexion"), "Lower should suggest KneeFlexion, not SpinalFlexion-for-hamstring");
      assert.ok(lowerPatterns.has("PlantarFlexion"), "Lower should suggest PlantarFlexion");
      // Lower must NOT suggest SpinalFlexion-for-hamstring or duplicate SpinalFlexion slots.
      const lowerAbSuggestions = lower.suggestedPatterns.filter((s) => s.pattern === "SpinalFlexion");
      assert.ok(lowerAbSuggestions.length <= 1, "Lower should not duplicate SpinalFlexion suggestions");
    });

    it("produces one DayPlan per training day (4-day UL×2)", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      assert.equal(plans.length, 4);
      assert.equal(plans[0].weekday, "Monday");
      assert.equal(plans[3].weekday, "Thursday");
    });

    it("every non-Omit major muscle appears on ≥2 days for a 4-day split", () => {
      const ctx = contextFor(4);
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      for (const muscle of ["Chest", "Back", "Quad", "Hamstring", "Glute"]) {
        const freq = frequencyFor(plans, muscle);
        assert.ok(freq >= 2, `${muscle} appears ${freq}×/week; need ≥2`);
      }
    });

    it("per-day muscle budget credits fit within the time budget", () => {
      const ctx = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Intermediate",
            sessionLengthMinutes: 110,
            weekdays: "Monday,Tuesday,Thursday,Friday",
            trainingDaysPerWeek: 4,
          },
          specialization: { Chest: "Specialize" },
          provenLifts: LIFT_CATALOG,
          availableLifts: [],
          literatureVolumeLandmarks: LANDMARKS,
        })
      );
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const timeBudget = plans[0].timeBudgetHardSets;
      assert.ok(timeBudget != null);
      for (const plan of plans) {
        const total = plan.muscleBudgets.reduce((s, b) => s + b.sets, 0);
        assert.ok(total <= (plan.timeBudgetHardSets ?? Infinity) + 2, `day ${plan.dayIndex} credits ${total} > budget ${plan.timeBudgetHardSets} (tolerance 2)`);
      }
    });

    it("weekly sum of per-day sets matches the weekly target aim within tolerance (Chest specialized)", () => {
      const ctx = contextFor(4, { specialization: { Chest: "Specialize" } });
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      const chestWeekly = weeklySumFor(plans, "Chest");
      const aim = targets.get("Chest")!.aim;
      // Specialize split is 60/40, so sum = aim * 1.2/2 + aim * 0.8/2 * (freq-1)... actually
      // with freq=2: day1 gets perDay*1.2, day2 gets perDay*0.8 → sum = perDay*(1.2+0.8) = 2*perDay = aim.
      assert.ok(Math.abs(chestWeekly - aim) <= 2, `Chest weekly ${chestWeekly} vs aim ${aim}`);
    });

    it("accessory spillover adds small-muscle volume to days with slack time", () => {
      // Lower days should pick up Ab/lower-back accessories if they have slack.
      const ctx = contextFor(4);
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const lowerDays = plans.filter((p) => p.theme.startsWith("Lower"));
      const anyAccessory = lowerDays.some((p) =>
        p.muscleBudgets.some((b) => b.muscle === "Ab" || b.muscle === "LowerBack"),
      );
      // Accessories only spillover if there's slack; with 75min sessions and these targets
      // lower days should have room for at least one accessory in at least one lower day.
      assert.ok(anyAccessory, "expected at least one accessory on a lower day");
    });

    it("never suggests a pattern the user has no enabled lift for", () => {
      // Remove all calf lifts — the allocator must not suggest a PlantarFlexion pattern.
      const ctx = contextFor(4);
      const noCalfCtx = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Intermediate",
            sessionLengthMinutes: 75,
            weekdays: "Monday,Tuesday,Thursday,Friday",
            trainingDaysPerWeek: 4,
          },
          specialization: {},
          provenLifts: LIFT_CATALOG.filter((l) => l.primaryMuscles[0] !== "Calf"),
          availableLifts: [],
          literatureVolumeLandmarks: LANDMARKS,
        }),
      );
      const plans = allocateDayPlans(noCalfCtx, computeAllWeeklyTargets(noCalfCtx));
      for (const plan of plans) {
        for (const s of plan.suggestedPatterns) {
          assert.ok(s.pattern !== "PlantarFlexion", `day ${plan.dayIndex} suggests PlantarFlexion with no enabled lift`);
        }
      }
    });

    it("returns an empty array when no deterministic template exists (7 days)", () => {
      const ctx = contextFor(7);
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      assert.equal(plans.length, 0);
    });

    it("5-day split produces 5 plans with Chest on ≥2 days", () => {
      const ctx = contextFor(5, { specialization: { Chest: "Specialize" } });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      assert.equal(plans.length, 5);
      const chestFreq = frequencyFor(plans, "Chest");
      assert.ok(chestFreq >= 2, `Chest on ${chestFreq} days; need ≥2`);
    });

    it("6-day PPL×2 produces 6 plans with push/pull/legs themes", () => {
      const ctx = contextFor(6, { specialization: {} });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      assert.equal(plans.length, 6);
      assert.ok(plans[0].name.startsWith("Push"));
      assert.ok(plans[1].name.startsWith("Pull"));
      assert.ok(plans[2].name.startsWith("Legs"));
    });

    it("handles a profile with no session length (time budget null, no trimming)", () => {
      const ctx = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Intermediate",
            weekdays: "Monday,Wednesday,Friday,Saturday",
            trainingDaysPerWeek: 4,
            // no sessionLengthMinutes
          },
          specialization: { Chest: "Specialize" },
          provenLifts: LIFT_CATALOG,
          availableLifts: [],
          literatureVolumeLandmarks: LANDMARKS,
        }),
      );
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      assert.equal(plans[0].timeBudgetHardSets, null);
      // Suggested patterns still produced; Phase 2 validates the final program.
      assert.ok(plans[0].suggestedPatterns.length > 0);
    });
  });
});