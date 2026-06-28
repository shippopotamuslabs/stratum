import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets } from "./volumeTargetPlanner";
import { allocateDayPlans, type DayPlan } from "./splitAllocator";
import { buildDayCandidates, type LiftCandidate } from "./liftPriority";
import { repBandsForLifts, type RepBand } from "./repBandPlanner";
import {
  deterministicFallbackWorkout,
  mergeWorkouts,
  repairWeeklyVolume,
  repairWorkout,
  type WorkoutBuilderResult,
} from "./programIntegration";
import { validateProgramMetrics } from "./programValidation";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8, fatigueDecayIntercept: 100 } },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep", "PosteriorDeltoid"], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "rdl", name: "Romanian Deadlift", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute", "LowerBack"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "DeltIso", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "curl", name: "Curl", movementPattern: "BicepIso", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "TricepIso", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "Calves", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "crunch", name: "Crunch", movementPattern: "AbIso", primaryMuscles: ["Ab"], secondaryMuscles: [] },
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
  { muscle: "Ab", estimatedMev: 2, estimatedMav: 6, estimatedmrv: 12, estimatedPv: 4, estimatedMrv: 12 },
];

function contextFor(days = 4) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return parseGenerationContext(
    JSON.stringify({
      profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, weekdays, trainingDaysPerWeek: days },
      specialization: { Chest: "Specialize" },
      provenLifts: LIFT_CATALOG,
      availableLifts: [],
      literatureVolumeLandmarks: LANDMARKS,
    }),
  );
}

function dayBundle(ctx: ReturnType<typeof contextFor>, dayPlan: DayPlan) {
  const candidates = buildDayCandidates(dayPlan, ctx);
  const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
  return { dayPlan, candidates, repBands, ctx };
}

describe("programIntegration", () => {
  describe("repairWorkout", () => {
    it("drops lifts whose liftId is not in the candidate set", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: [{ position: 0 }, { position: 1 }, { position: 2 }] },
          { liftId: "phantom-press", position: 1, workoutLiftSets: [{ position: 0 }] },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      assert.ok(repaired.workoutLifts!.some((l) => l.liftId === "bench"));
      assert.ok(!repaired.workoutLifts!.some((l) => l.liftId === "phantom-press"));
    });

    it("clamps reps to the candidate's rep band", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const band = repBands.get("bench")!;
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: [
            { position: 0, repMin: 1, repMax: 2, isTopSet: true },
            { position: 1, repMin: 30, repMax: 40, isTopSet: false },
          ] },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      const sets = repaired.workoutLifts![0].workoutLiftSets as Array<{ repMin: number; repMax: number }>;
      assert.ok(sets[0].repMin >= band.repMin && sets[0].repMax <= band.repMax, `set0 ${JSON.stringify(sets[0])} outside ${JSON.stringify(band)}`);
      assert.ok(sets[1].repMin >= band.repMin && sets[1].repMax <= band.repMax, `set1 ${JSON.stringify(sets[1])} outside ${JSON.stringify(band)}`);
    });

    it("forces exactly one top set for Double-progression lifts (Intermediate)", () => {
      const ctx = contextFor(); // Intermediate → Double
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: [
            { position: 0, isTopSet: true },
            { position: 1, isTopSet: true },
            { position: 2, isTopSet: false },
          ] },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      const sets = repaired.workoutLifts![0].workoutLiftSets as Array<{ isTopSet: boolean }>;
      const topSets = sets.filter((s) => s.isTopSet);
      // First set is forced top; subsequent forced non-top.
      assert.equal(sets[0].isTopSet, true);
      assert.equal(topSets.length, 1);
    });

    it("backfills sets when a muscle is under its per-day min", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      // Only 1 set of bench — Chest will be far under its min.
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: [{ position: 0, isTopSet: true }] },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      const benchSets = repaired.workoutLifts!.find((l) => l.liftId === "bench")?.workoutLiftSets;
      assert.ok(benchSets!.length > 1, `backfill should add sets; got ${benchSets!.length}`);
    });

    it("trims total hard sets to the time budget", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      // 30 sets on bench — far over the time budget.
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: Array.from({ length: 30 }, (_, i) => ({ position: i })) },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      const total = repaired.workoutLifts!.reduce((s, l) => s + (l.workoutLiftSets?.length ?? 0), 0);
      assert.ok(total <= (plan.timeBudgetHardSets ?? Infinity), `total ${total} > budget ${plan.timeBudgetHardSets}`);
    });

    it("produces a valid workout from an empty LLM result (backfill only)", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const repaired = repairWorkout({ name: plan.name, workoutLifts: [] }, plan, candidates, repBands, ctx);
      // Backfill should produce at least one lift for each muscle budget that has a candidate.
      assert.ok((repaired.workoutLifts!.length) > 0, "empty draft should backfill to non-empty");
    });

    it("merges duplicate liftId entries within a workout (Bug 1 fix)", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      // bench appears in two slots — the LLM repeating the same lift.
      const draft = {
        name: plan.name,
        workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: [{ position: 0 }, { position: 1 }] },
          { liftId: "bench", position: 1, workoutLiftSets: [{ position: 0 }] },
          { liftId: "row", position: 2, workoutLiftSets: [{ position: 0 }, { position: 1 }] },
        ],
      };
      const repaired = repairWorkout(draft as never, plan, candidates, repBands, ctx);
      const benchEntries = repaired.workoutLifts!.filter((l) => l.liftId === "bench");
      assert.equal(benchEntries.length, 1, "duplicate bench entries should be merged into one");
      // Merged set count: 2 + 1 = 3 (clamped to slot maxSets, which is ≥3 for a Chest slot).
      assert.ok(benchEntries[0].workoutLiftSets!.length >= 2, "merged bench should retain combined sets");
    });
  });

  describe("deterministicFallbackWorkout", () => {
    it("produces one lift per pattern slot with band-midpoint reps", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const fallback = deterministicFallbackWorkout(plan, candidates, repBands, ctx);
      assert.ok(fallback.workoutLifts!.length > 0);
      // Each lift should have ≥2 sets.
      for (const lift of fallback.workoutLifts!) {
        assert.ok((lift.workoutLiftSets?.length ?? 0) >= 2);
      }
    });

    it("produces a Double top set on the first set for Intermediate", () => {
      const ctx = contextFor();
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Upper"))!;
      const { candidates, repBands } = dayBundle(ctx, plan);
      const fallback = deterministicFallbackWorkout(plan, candidates, repBands, ctx);
      const firstLift = fallback.workoutLifts![0];
      const sets = firstLift.workoutLiftSets as Array<{ isTopSet: boolean }>;
      assert.equal(sets[0].isTopSet, true);
      assert.equal(sets.filter((s) => s.isTopSet).length, 1);
    });

    it("rotates candidate picks across sibling days so siblings differ (Bug 2 fix, fallback path)", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      // Find the two Upper sibling day plans.
      const upperPlans = plans
        .map((p, i) => ({ plan: p, index: i }))
        .filter(({ plan }) => plan.theme.startsWith("Upper"));
      assert.ok(upperPlans.length >= 2, "4-day split should have two Upper sibling days");

      const first = upperPlans[0];
      const second = upperPlans[1];
      const firstCands = buildDayCandidates(first.plan, ctx);
      const secondCands = buildDayCandidates(second.plan, ctx);
      const firstBands = repBandsForLifts(firstCands.map((c) => c.liftId), ctx);
      const secondBands = repBandsForLifts(secondCands.map((c) => c.liftId), ctx);

      const a = deterministicFallbackWorkout(first.plan, firstCands, firstBands, ctx, plans, first.index);
      const b = deterministicFallbackWorkout(second.plan, secondCands, secondBands, ctx, plans, second.index);
      const aIds = new Set(a.workoutLifts!.map((l) => l.liftId));
      const bIds = new Set(b.workoutLifts!.map((l) => l.liftId));
      // When ≥2 candidates exist for at least one shared pattern, the rotated pick should differ.
      assert.ok(
        [...aIds].some((id) => !bIds.has(id)),
        `rotated siblings should differ: Upper A ${[...aIds]} vs Upper B ${[...bIds]}`,
      );
    });
  });

  describe("mergeWorkouts", () => {
    it("assembles N workouts into a program with sequential positions", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      const results: WorkoutBuilderResult[] = plans.map((plan) => {
        const candidates = buildDayCandidates(plan, ctx);
        const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
        return { dayPlan: plan, candidates, repBands, raw: null };
      });
      const program = mergeWorkouts(results, ctx);
      assert.equal(program.workouts.length, 4);
      assert.equal(program.workouts[0].position, 0);
      assert.equal(program.workouts[3].position, 3);
    });

    it("uses the deterministic fallback when raw is null", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      const results: WorkoutBuilderResult[] = plans.map((plan) => {
        const candidates = buildDayCandidates(plan, ctx);
        const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
        return { dayPlan: plan, candidates, repBands, raw: null };
      });
      const program = mergeWorkouts(results, ctx);
      // Every workout should be non-empty (fallback fills it).
      for (const workout of program.workouts) {
        assert.ok((workout.workoutLifts?.length ?? 0) > 0, "fallback workout should be non-empty");
      }
    });
  });

  describe("repairWeeklyVolume", () => {
    it("adds sets when a muscle is under its weekly min", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      // A program with only 1 bench set all week — Chest is far under its weekly min.
      const program = {
        name: "Test",
        workouts: [
          { name: "Upper", position: 0, weekday: "Monday", workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: [{ position: 0, repMin: 6, repMax: 8, rirGoal: 2, isTopSet: true }] },
          ] },
        ],
      };
      const before = validateProgramMetrics(program, ctx).summary.weeklyMuscleVolume.Chest ?? 0;
      const repaired = repairWeeklyVolume(program as never, targets, ctx);
      const after = validateProgramMetrics(repaired, ctx).summary.weeklyMuscleVolume.Chest ?? 0;
      assert.ok(after > before, `Chest ${after} should exceed ${before} after backfill`);
    });
  });
});