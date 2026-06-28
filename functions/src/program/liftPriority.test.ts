import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets } from "./volumeTargetPlanner";
import { allocateDayPlans } from "./splitAllocator";
import {
  buildDayCandidates,
  CANDIDATES_PER_SLOT,
  scoreLift,
} from "./liftPriority";

function contextWith(overrides: {
  provenLifts?: unknown[];
  availableLifts?: unknown[];
  responseProfile?: unknown;
  specialization?: Record<string, string>;
  days?: number;
} = {}) {
  const days = overrides.days ?? 4;
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Intermediate",
        sessionLengthMinutes: 75,
        weekdays,
        trainingDaysPerWeek: days,
      },
      specialization: overrides.specialization ?? { Chest: "Specialize" },
      provenLifts: overrides.provenLifts ?? [
        { id: "bench", name: "Bench Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8, fatigueDecayIntercept: 100 } },
        { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
      ],
      availableLifts: overrides.availableLifts ?? [
        { id: "inc-press", name: "Incline DB Press", movementPattern: "InclinePush", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
        { id: "machine-press", name: "Chest Press Machine", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
      ],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
      ],
      ...(overrides.responseProfile ? { responseProfile: overrides.responseProfile } : {}),
    }),
  );
}

describe("liftPriority", () => {
  describe("scoreLift", () => {
    it("a proven, improving, rank-1 lift scores higher than an available lift with no insights", () => {
      const ctx = contextWith({
        responseProfile: {
          liftEffectiveness: [
            { pattern: "HorizontalPush", liftId: "bench", rank: 1, evidence: "Hypothesis" },
          ],
        },
      });
      const bench = ctx.liftsById.get("bench")!;
      const machine = ctx.liftsById.get("machine-press")!;
      const benchScore = scoreLift(bench, ctx);
      const machineScore = scoreLift(machine, ctx);
      assert.ok(benchScore > machineScore, `bench ${benchScore} should outrank machine ${machineScore}`);
    });

    it("a proven lift with Stagnant trend scores lower than the same lift with Improving trend", () => {
      const improvingCtx = contextWith({
        provenLifts: [
          { id: "lift-a", name: "A", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
        ],
      });
      const stagnantCtx = contextWith({
        provenLifts: [
          { id: "lift-b", name: "B", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
        ],
      });
      const improvingScore = scoreLift(improvingCtx.liftsById.get("lift-a")!, improvingCtx);
      const stagnantScore = scoreLift(stagnantCtx.liftsById.get("lift-b")!, stagnantCtx);
      assert.ok(improvingScore > stagnantScore, `Improving ${improvingScore} > Stagnant ${stagnantScore}`);
    });

    it("a lift with effectiveness rank 1 scores higher than rank 2 (same tier, same trend)", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "rank1", name: "R1", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
          { id: "rank2", name: "R2", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
        ],
        responseProfile: {
          liftEffectiveness: [
            { pattern: "HorizontalPush", liftId: "rank1", rank: 1, evidence: "Hypothesis" },
            { pattern: "HorizontalPush", liftId: "rank2", rank: 2, evidence: "Hypothesis" },
          ],
        },
      });
      const s1 = scoreLift(ctx.liftsById.get("rank1")!, ctx);
      const s2 = scoreLift(ctx.liftsById.get("rank2")!, ctx);
      assert.ok(s1 > s2, `rank-1 ${s1} > rank-2 ${s2}`);
    });

    it("score is in [0, 1]", () => {
      const ctx = contextWith();
      for (const lift of ctx.liftsById.values()) {
        const s = scoreLift(lift, ctx);
        assert.ok(s >= 0 && s <= 1, `${lift.id} score ${s} out of [0,1]`);
      }
    });
  });

  describe("buildDayCandidates", () => {
    it("returns only lifts whose pattern matches a day's pattern slots", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      // Upper day 0 has HorizontalPush + HorizontalPull slots → bench + row should both appear;
      // the Lower day has no HorizontalPush slot → bench should not appear there.
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const upperCandidates = buildDayCandidates(upperPlan, ctx);
      const upperIds = upperCandidates.map((c) => c.liftId);
      assert.ok(upperIds.includes("bench"));
      assert.ok(upperIds.includes("row"));

      const lowerPlan = plans.find((p) => p.theme.startsWith("Lower"))!;
      const lowerCandidates = buildDayCandidates(lowerPlan, ctx);
      assert.ok(
        !lowerCandidates.some((c) => c.liftId === "bench"),
        "bench (HorizontalPush) should not appear on a Lower day",
      );
    });

    it("caps candidates to CANDIDATES_PER_SLOT per pattern slot", () => {
      // Many HorizontalPush lifts → only top-K should appear for that slot.
      const ctx = contextWith({
        provenLifts: [
          { id: "h1", name: "H1", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 20, progressTrend: "Improving" } },
          { id: "h2", name: "H2", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 15, progressTrend: "Improving" } },
          { id: "h3", name: "H3", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
          { id: "h4", name: "H4", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 8, progressTrend: "Stagnant" } },
          { id: "h5", name: "H5", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 5, progressTrend: "Regressing" } },
          { id: "h6", name: "H6", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [] },
          { id: "h7", name: "H7", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const horizontalPushCandidates = buildDayCandidates(upperPlan, ctx)
        .filter((c) => c.pattern === "HorizontalPush");
      assert.ok(
        horizontalPushCandidates.length <= CANDIDATES_PER_SLOT,
        `got ${horizontalPushCandidates.length} > ${CANDIDATES_PER_SLOT}`,
      );
    });

    it("candidates are sorted by priority descending within a pattern", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "bench", name: "Bench", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 20, progressTrend: "Improving" } },
          { id: "machine-press", name: "Machine", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const hpush = buildDayCandidates(upperPlan, ctx).filter((c) => c.pattern === "HorizontalPush");
      assert.ok(hpush.length >= 2);
      assert.ok(hpush[0].priority >= hpush[1].priority, "sorted desc");
    });

    it("trimmed insights include fatigueDecaySlope and progressTrend, not sessionCount or estimatedOneRm", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const bench = buildDayCandidates(upperPlan, ctx).find((c) => c.liftId === "bench");
      assert.ok(bench, "bench candidate missing");
      assert.ok(bench!.trainingInsights, "bench should carry trimmed insights");
      assert.equal(bench!.trainingInsights!.progressTrend, "Improving");
      assert.equal(bench!.trainingInsights!.fatigueDecaySlope, -0.8);
      // sessionCount is NOT in the trimmed builder payload.
      assert.equal("sessionCount" in (bench!.trainingInsights! as Record<string, unknown>), false);
    });

    it("a lift with no matching pattern slot on a day is excluded", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const lowerPlan = plans.find((p) => p.theme.startsWith("Lower"))!;
      const candidates = buildDayCandidates(lowerPlan, ctx);
      // No upper-push lifts should appear on a lower day.
      assert.ok(!candidates.some((c) => c.liftId === "bench"));
    });
  });
});