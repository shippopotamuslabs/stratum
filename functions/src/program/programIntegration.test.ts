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
      trimSessionMuscleCreditCaps,
      trimWorkoutsToSessionDuration,
      type WorkoutBuilderResult,
} from "./programIntegration";
import { validateProgramMetrics } from "./programValidation";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8, fatigueDecayIntercept: 100 } },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep", "PosteriorDeltoid"], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "rdl", name: "Romanian Deadlift", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute", "LowerBack"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "ShoulderAbduction", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "crunch", name: "Crunch", movementPattern: "SpinalFlexion", primaryMuscles: ["Ab"], secondaryMuscles: [] },
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
      const aList = a.workoutLifts!.map((l) => l.liftId);
      const bList = b.workoutLifts!.map((l) => l.liftId);
      // The workouts must differ in their exercise selection or order.
      assert.ok(
        aList.some((id, idx) => bList[idx] !== id),
        `rotated siblings should differ: Upper A ${aList} vs Upper B ${bList}`,
      );
    });

    it("does not repeat the same liftId within a workout even with overlapping patterns", () => {
      // The fallback tracks usedLiftIds so no lift is picked twice, even when multiple suggested
      // patterns could resolve to the same candidate (e.g. a compound covering two muscles).
      const ctx = contextFor(4);
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme.startsWith("Lower"))!;
      const candidates = buildDayCandidates(plan, ctx);
      const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
      const fallback = deterministicFallbackWorkout(plan, candidates, repBands, ctx);
      const liftIds = fallback.workoutLifts!.map((lift) => lift.liftId);
      assert.equal(new Set(liftIds).size, liftIds.length, "fallback should not duplicate liftIds within one workout");
    });

    it("fills a 75-minute advanced upper day with arm specialization (volume + variety)", () => {
      const weekdays = "Monday,Tuesday,Thursday,Friday";
      const ctx = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Advanced",
            sessionLengthMinutes: 75,
            weekdays,
            trainingDaysPerWeek: 4,
          },
          specialization: { Bicep: "Specialize", Tricep: "Specialize" },
          provenLifts: LIFT_CATALOG,
          availableLifts: [],
          literatureVolumeLandmarks: LANDMARKS,
        }),
      );
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upper = plans.find((p) => p.theme === "Upper A")!;
      const candidates = buildDayCandidates(upper, ctx);
      const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
      const fallback = deterministicFallbackWorkout(upper, candidates, repBands, ctx, plans, 0);
      const program = mergeWorkouts(
        plans.map((plan, index) => ({
          dayPlan: plan,
          dayIndex: index,
          allDayPlans: plans,
          candidates: buildDayCandidates(plan, ctx),
          repBands: repBandsForLifts(buildDayCandidates(plan, ctx).map((c) => c.liftId), ctx),
          raw: null,
        })),
        ctx,
      );
      const upperWorkout = program.workouts.find((w) => w.name === "Upper A")!;
      const exerciseCount = upperWorkout.workoutLifts?.length ?? 0;
      const totalSets = (upperWorkout.workoutLifts ?? []).reduce(
        (sum, lift) => sum + (lift.workoutLiftSets?.length ?? 0),
        0,
      );
      const liftIds = (upperWorkout.workoutLifts ?? []).map((l) => l.liftId);
      const armLifts = liftIds.filter((id) => id === "curl" || id === "tricep-ext");
      assert.ok(exerciseCount >= 5 && exerciseCount <= 8, `expected 5–8 exercises, got ${exerciseCount}`);
      assert.ok(totalSets >= 15, `upper fallback should use most of the session (${totalSets} sets)`);
      assert.ok(armLifts.length >= 2, "arm specialization should surface both curl and tricep work");
      assert.ok(
        (fallback.workoutLifts ?? []).some((l) => {
          const sets = l.workoutLiftSets as Array<{ repMin: number; repMax: number }>;
          return sets[0].repMin <= 8 && sets[0].repMax >= 10;
        }),
        "compounds should use lower rep ranges than isolations",
      );
    });

    it("collapses an over-generated LLM-style upper workout to commercial size", () => {
      const ctx = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Advanced",
            sessionLengthMinutes: 75,
            weekdays: "Monday,Tuesday,Thursday,Friday",
            trainingDaysPerWeek: 4,
          },
          specialization: { Bicep: "Specialize", Tricep: "Specialize" },
          provenLifts: LIFT_CATALOG,
          availableLifts: [],
          literatureVolumeLandmarks: LANDMARKS,
        }),
      );
      const plan = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx)).find((p) => p.theme === "Upper A")!;
      const candidates = buildDayCandidates(plan, ctx);
      const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
      const threeSets = [{ position: 0, repMin: 8, repMax: 12, rirGoal: 2, isTopSet: true }, { position: 1, repMin: 8, repMax: 12, rirGoal: 0, isTopSet: false }, { position: 2, repMin: 8, repMax: 12, rirGoal: 0, isTopSet: false }];
      const bloated = {
        name: plan.name,
        workoutLifts: [
          "bench", "row", "db-press", "lat-raise", "curl", "tricep-ext", "seated-row", "crunch",
        ].map((liftId, position) => ({ liftId, position, workoutLiftSets: threeSets })),
      };
      const repaired = repairWorkout(bloated as never, plan, candidates, repBands, ctx);
      assert.ok(
        (repaired.workoutLifts?.length ?? 0) <= plan.targetExerciseCount,
        `repair should cap LLM bloat (got ${repaired.workoutLifts?.length})`,
      );
      const hingeLike = (repaired.workoutLifts ?? []).filter((l) => /deadlift|romanian|stiff/i.test(l.liftId ?? ""));
      assert.equal(hingeLike.length, 0, "upper day should not contain hip-hinge deadlift variants");
    });

    it("does not stack two hip-hinge compounds on the same lower day", () => {
      const ctx = contextFor(4);
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      for (const plan of plans.filter((p) => p.theme.startsWith("Lower"))) {
        const candidates = buildDayCandidates(plan, ctx);
        const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
        const program = mergeWorkouts(
          [{
            dayPlan: plan,
            dayIndex: plan.dayIndex,
            allDayPlans: plans,
            candidates,
            repBands,
            raw: null,
          }],
          ctx,
        );
        const workout = program.workouts[0];
        const hingeCount = (workout.workoutLifts ?? []).filter((l) => {
          const c = candidates.find((x) => x.liftId === l.liftId);
          return c != null && (c.pattern === "HipHinge" || /deadlift|romanian|stiff-?leg|good-morning/i.test(c.liftId));
        }).length;
        assert.ok(hingeCount <= 1, `${plan.name} should have at most one hip-hinge lift`);
      }
    });
  });

  describe("mergeWorkouts", () => {
    it("assembles N workouts into a program with sequential positions", () => {
      const ctx = contextFor(4);
      const targets = computeAllWeeklyTargets(ctx);
      const plans = allocateDayPlans(ctx, targets);
      const results: WorkoutBuilderResult[] = plans.map((plan, index) => {
        const candidates = buildDayCandidates(plan, ctx);
        const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
        return { dayPlan: plan, dayIndex: index, allDayPlans: plans, candidates, repBands, raw: null };
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
      const results: WorkoutBuilderResult[] = plans.map((plan, index) => {
        const candidates = buildDayCandidates(plan, ctx);
        const repBands = repBandsForLifts(candidates.map((c) => c.liftId), ctx);
        return { dayPlan: plan, dayIndex: index, allDayPlans: plans, candidates, repBands, raw: null };
      });
      const program = mergeWorkouts(results, ctx);
      // Every workout should be non-empty (fallback fills it).
      for (const workout of program.workouts) {
        assert.ok((workout.workoutLifts?.length ?? 0) > 0, "fallback workout should be non-empty");
        assert.ok(
          (workout.workoutLifts?.length ?? 0) <= (plans[workout.position ?? 0].targetExerciseCount),
          `fallback should cap exercises per session (got ${workout.workoutLifts?.length})`,
        );
        const totalSets = (workout.workoutLifts ?? []).reduce(
          (sum, lift) => sum + (lift.workoutLiftSets?.length ?? 0),
          0,
        );
        const plan = plans[workout.position ?? 0];
        const timeBudget = plan.timeBudgetHardSets ?? 0;
        const minSets = plan.theme.toLowerCase().includes("lower")
          ? Math.min(12, Math.floor(timeBudget * 0.55))
          : Math.floor(timeBudget * 0.65);
        assert.ok(
          totalSets >= minSets,
          `fallback should fill a sensible session (${totalSets}/${timeBudget} sets, min ${minSets})`,
        );
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

    it("trimSessionMuscleCreditCaps removes excess fractional credits without exceeding session cap", () => {
      const ctx = contextFor(4);
      const program = {
        name: "Test",
        workouts: [{
          name: "Upper",
          position: 0,
          weekday: null,
          workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: Array.from({ length: 5 }, (_, i) => ({ position: i, repMin: 8, repMax: 12, rirGoal: 2, isTopSet: i === 0 })) },
            { liftId: "tricep-ext", position: 1, workoutLiftSets: Array.from({ length: 6 }, (_, i) => ({ position: i, repMin: 10, repMax: 15, rirGoal: 1, isTopSet: i === 0 })) },
          ],
        }],
      };
      trimSessionMuscleCreditCaps(program, ctx);
      const report = validateProgramMetrics(program, ctx);
      const sessionHigh = report.issues.filter((i) => i.code === "SESSION_MUSCLE_VOLUME_HIGH");
      assert.equal(sessionHigh.length, 0);
    });

    it("trimWorkoutsToSessionDuration keeps workouts within session length + 5 minutes", () => {
      const ctx = contextFor(4);
      const ctx30 = parseGenerationContext(
        JSON.stringify({
          profile: {
            trainingAge: "Advanced",
            sessionLengthMinutes: 30,
            weekdays: "Monday,Tuesday,Thursday,Friday",
            trainingDaysPerWeek: 4,
          },
          specialization: { Bicep: "Specialize", Tricep: "Specialize" },
          provenLifts: LIFT_CATALOG,
          availableLifts: [],
          literatureVolumeLandmarks: [
            { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
            { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
            { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
            { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
          ],
        }),
      );
      const program = {
        name: "Test",
        workouts: [{
          name: "Upper",
          position: 0,
          weekday: null,
          workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: Array.from({ length: 6 }, (_, i) => ({ position: i })) },
            { liftId: "curl", position: 1, workoutLiftSets: Array.from({ length: 6 }, (_, i) => ({ position: i })) },
            { liftId: "tricep-ext", position: 2, workoutLiftSets: Array.from({ length: 6 }, (_, i) => ({ position: i })) },
          ],
        }],
      };
      trimWorkoutsToSessionDuration(program, ctx30);
      const report = validateProgramMetrics(program, ctx30);
      assert.ok(report.summary.estimatedWorkoutMinutes[0]! <= 35);
    });
  });
});