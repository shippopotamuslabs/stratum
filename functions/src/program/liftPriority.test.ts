import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets } from "./volumeTargetPlanner";
import { allocateDayPlans } from "./splitAllocator";
import {
  buildDayCandidates,
  buildGlobalCandidates,
  CANDIDATES_PER_SLOT,
  compareLiftCandidates,
  mainstreamNameScore,
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
        { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8, fatigueDecayIntercept: 100 } },
        { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
      ],
      availableLifts: overrides.availableLifts ?? [
        { id: "inc-press", name: "Incline DB Press", movementPattern: "InclinePress", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
        { id: "machine-press", name: "Chest Press Machine", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
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
            { pattern: "HorizontalPress", liftId: "bench", rank: 1, evidence: "Hypothesis" },
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
          { id: "lift-a", name: "A", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
        ],
      });
      const stagnantCtx = contextWith({
        provenLifts: [
          { id: "lift-b", name: "B", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
        ],
      });
      const improvingScore = scoreLift(improvingCtx.liftsById.get("lift-a")!, improvingCtx);
      const stagnantScore = scoreLift(stagnantCtx.liftsById.get("lift-b")!, stagnantCtx);
      assert.ok(improvingScore > stagnantScore, `Improving ${improvingScore} > Stagnant ${stagnantScore}`);
    });

    it("a lift with effectiveness rank 1 scores higher than rank 2 (same tier, same trend)", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "rank1", name: "R1", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
          { id: "rank2", name: "R2", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Improving" } },
        ],
        responseProfile: {
          liftEffectiveness: [
            { pattern: "HorizontalPress", liftId: "rank1", rank: 1, evidence: "Hypothesis" },
            { pattern: "HorizontalPress", liftId: "rank2", rank: 2, evidence: "Hypothesis" },
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
    it("returns lifts that train the day's targeted muscles, excludes off-day lifts", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      // Upper day targets Chest/Back/etc → bench + row should both appear (they hit those muscles);
      // the Lower day targets Quad/Hamstring/etc → bench (Chest) should not appear there.
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const upperCandidates = buildDayCandidates(upperPlan, ctx);
      const upperIds = upperCandidates.map((c) => c.liftId);
      assert.ok(upperIds.includes("bench"));
      assert.ok(upperIds.includes("row"));

      const lowerPlan = plans.find((p) => p.theme.startsWith("Lower"))!;
      const lowerCandidates = buildDayCandidates(lowerPlan, ctx);
      assert.ok(
        !lowerCandidates.some((c) => c.liftId === "bench"),
        "bench (Chest) should not appear on a Lower day",
      );
    });

    it("caps candidates to CANDIDATES_PER_SLOT per pattern", () => {
      // Many HorizontalPress lifts → only top-K should appear for that slot.
      const ctx = contextWith({
        provenLifts: [
          { id: "h1", name: "H1", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 20, progressTrend: "Improving" } },
          { id: "h2", name: "H2", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 15, progressTrend: "Improving" } },
          { id: "h3", name: "H3", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
          { id: "h4", name: "H4", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 8, progressTrend: "Stagnant" } },
          { id: "h5", name: "H5", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 5, progressTrend: "Regressing" } },
          { id: "h6", name: "H6", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
          { id: "h7", name: "H7", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const horizontalPushCandidates = buildDayCandidates(upperPlan, ctx)
        .filter((c) => c.pattern === "HorizontalPress");
      assert.ok(
        horizontalPushCandidates.length <= CANDIDATES_PER_SLOT,
        `got ${horizontalPushCandidates.length} > ${CANDIDATES_PER_SLOT}`,
      );
    });

    it("candidates are sorted by priority descending within a pattern", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "bench", name: "Bench", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 20, progressTrend: "Improving" } },
          { id: "machine-press", name: "Machine", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const hpush = buildDayCandidates(upperPlan, ctx).filter((c) => c.pattern === "HorizontalPress");
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

    it("a lift that trains none of the day's targeted muscles is excluded", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const lowerPlan = plans.find((p) => p.theme.startsWith("Lower"))!;
      const candidates = buildDayCandidates(lowerPlan, ctx);
      // No upper-push lifts should appear on a lower day.
      assert.ok(!candidates.some((c) => c.liftId === "bench"));
    });

    it("prefers bread-and-butter lifts over alternating catalog tail when priorities tie", () => {
      const ctx = contextWith({
        provenLifts: [],
        availableLifts: [
          { id: "alt-press", name: "Alternating Floor Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
          { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
        ],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const upperPlan = plans.find((p) => p.theme.startsWith("Upper"))!;
      const hpush = buildDayCandidates(upperPlan, ctx).filter((c) => c.pattern === "HorizontalPress");
      assert.ok(hpush.length >= 2);
      assert.equal(hpush[0].liftId, "bench", "bench should outrank alternating floor press");
      assert.ok(mainstreamNameScore("Bench Press") > mainstreamNameScore("Alternating Floor Press"));
    });

    it("compareLiftCandidates breaks priority ties with the mainstream flag", () => {
      const a = {
        liftId: "alt",
        name: "Alternating Kettlebell Row",
        pattern: "HorizontalPull",
        priority: 0.4,
        priorityTier: "available" as const,
        isCompound: true,
        mainstream: false,
        primaryMuscles: ["Back"],
        secondaryMuscles: [],
      };
      const b = {
        liftId: "row",
        name: "Barbell Row",
        pattern: "HorizontalPull",
        priority: 0.4,
        priorityTier: "available" as const,
        isCompound: true,
        mainstream: true,
        primaryMuscles: ["Back"],
        secondaryMuscles: [],
      };
      assert.ok(compareLiftCandidates(b, a) < 0, "mainstream barbell row should sort ahead of non-mainstream alternating row");
    });
  });

  describe("buildGlobalCandidates", () => {
    it("unifies candidates across all days and dedupes by liftId", () => {
      const ctx = contextWith({ days: 4 });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const global = buildGlobalCandidates(plans, ctx);
      const ids = global.map((c) => c.liftId);
      assert.equal(new Set(ids).size, ids.length, "global pool must not duplicate a liftId");

      // bench appears on Upper days; the global pool must include it (it trains a targeted muscle).
      assert.ok(ids.includes("bench"), "bench should be in the global pool");
    });

    it("excludes lifts that train none of any day's targeted muscles", () => {
      // Add an Ab-only lift; with a 4-day Upper/Lower split Ab is not a targeted muscle, so it
      // should not surface in the global pool.
      const ctx = contextWith({
        provenLifts: [
          { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], trainingInsights: { sessionCount: 12, progressTrend: "Improving" } },
          { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
          { id: "crunch", name: "Crunch", movementPattern: "SpinalFlexion", primaryMuscles: ["Ab"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const global = buildGlobalCandidates(plans, ctx);
      assert.ok(!global.some((c) => c.liftId === "crunch"), "Ab-only lift should not appear when Ab is never targeted");
    });

    it("caps each pattern to CANDIDATES_PER_SLOT", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "h1", name: "H1", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 20, progressTrend: "Improving" } },
          { id: "h2", name: "H2", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 15, progressTrend: "Improving" } },
          { id: "h3", name: "H3", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 10, progressTrend: "Stagnant" } },
          { id: "h4", name: "H4", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 8, progressTrend: "Stagnant" } },
          { id: "h5", name: "H5", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [], trainingInsights: { sessionCount: 5, progressTrend: "Regressing" } },
          { id: "h6", name: "H6", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
          { id: "h7", name: "H7", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: [] },
          { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
        ],
        availableLifts: [],
        days: 4,
      });
      const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
      const global = buildGlobalCandidates(plans, ctx);
      const hpush = global.filter((c) => c.pattern === "HorizontalPress");
      assert.ok(hpush.length <= CANDIDATES_PER_SLOT, `got ${hpush.length} > ${CANDIDATES_PER_SLOT}`);
    });
  });
});