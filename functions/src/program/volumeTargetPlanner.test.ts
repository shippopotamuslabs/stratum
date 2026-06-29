import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import {
  availablePatternsByMuscle,
  computeAllWeeklyTargets,
  resolveMuscleWeeklyTarget,
} from "./volumeTargetPlanner";

/**
 * Phase 0 volume-target planner tests. [resolveMuscleWeeklyTarget] is the byte-for-byte
 * extraction of the former `resolveVolumeTarget` from `programValidation.ts`, so these also
 * pin the migrated behavior. [computeAllWeeklyTargets] and [availablePatternsByMuscle] are new.
 */

function contextWith(overrides: Record<string, unknown> = {}) {
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Intermediate",
        sessionLengthMinutes: 60,
        weekdays: "Monday,Wednesday,Friday,Saturday",
        trainingDaysPerWeek: 4,
        ...("profile" in overrides ? (overrides.profile as object) : {}),
      },
      specialization: overrides.specialization ?? { Chest: "Specialize" },
      provenLifts: overrides.provenLifts ?? [
        {
          id: "bench",
          movementPattern: "HorizontalPress",
          primaryMuscles: ["Chest"],
          secondaryMuscles: ["Tricep"],
        },
        {
          id: "row",
          movementPattern: "HorizontalPull",
          primaryMuscles: ["Back"],
          secondaryMuscles: ["Bicep"],
        },
      ],
      availableLifts: overrides.availableLifts ?? [],
      literatureVolumeLandmarks:
        overrides.literatureVolumeLandmarks ?? [
          { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
          { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        ],
      ...(("responseProfile" in overrides
        ? { responseProfile: overrides.responseProfile }
        : {})),
    }),
  );
}

describe("volumeTargetPlanner", () => {
  describe("resolveMuscleWeeklyTarget", () => {
    it("Specialize without proven volume response aims between MAV and MRV", () => {
      const ctx = contextWith();
      const target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx);
      // Chest specialization, no proven volume response: aim = min(mrv, mav + 0.6*(mrv-mav)) = 14 + 0.6*6 = 17.6
      assert.equal(target.min, 12); // max(mev=8, mav-2=12)
      assert.equal(target.max, 20); // MRV
      assert.ok(target.aim > 14 && target.aim < 20, `aim ${target.aim} should sit between MAV and MRV`);
    });

    it("Specialize with proven positive volume response aims at MRV", () => {
      const ctx = contextWith({
        responseProfile: {
          volumeResponses: [
            { muscle: "Chest", correlationSlope: 0.4, evidence: "Proven" },
          ],
        },
      });
      const target = resolveMuscleWeeklyTarget("Chest", "Specialize", ctx);
      assert.equal(target.aim, 20); // respondsToVolume → aim = MRV
      assert.equal(target.max, 20);
    });

    it("Maintenance on a non-conservative profile targets MAV", () => {
      const ctx = contextWith({ specialization: {}, profile: { lastTrained: "WithinLastMonth" } });
      const target = resolveMuscleWeeklyTarget("Back", "Maintenance", ctx);
      assert.equal(target.min, 8); // MEV
      assert.equal(target.aim, 14); // MAV
      assert.equal(target.max, 20); // MRV
    });

    it("Maintenance on a conservative (return-to-training) profile targets PV, caps at MAV", () => {
      const ctx = contextWith({
        specialization: {},
        profile: { lastTrained: "MoreThanAYearAgo" },
      });
      const target = resolveMuscleWeeklyTarget("Back", "Maintenance", ctx);
      assert.equal(target.min, 8); // MEV
      assert.equal(target.aim, 11); // PV
      assert.equal(target.max, 14); // MAV (not MRV)
    });

    it("falls back to coarse defaults when no landmark exists for a muscle", () => {
      const ctx = contextWith({
        specialization: {},
        profile: { lastTrained: "WithinLastMonth" },
        literatureVolumeLandmarks: [],
      });
      const target = resolveMuscleWeeklyTarget("Hamstring", "Maintenance", ctx);
      assert.equal(target.min, 8); // default MEV
      assert.equal(target.aim, 14); // default MAV
      assert.equal(target.max, 20); // default MRV
    });
  });

  describe("computeAllWeeklyTargets", () => {
    it("returns a target for every non-Omitted major muscle and any specialized muscle", () => {
      const ctx = contextWith({ specialization: { Chest: "Specialize" } });
      const targets = computeAllWeeklyTargets(ctx);
      // Every MAJOR_MUSCLE should be present (none omitted).
      assert.ok(targets.has("Chest"));
      assert.ok(targets.has("Back"));
      assert.ok(targets.has("Quad"));
      assert.ok(targets.has("Hamstring"));
      assert.ok(targets.has("Glute"));
      assert.ok(targets.has("Tricep"));
      assert.ok(targets.has("Bicep"));
      assert.ok(targets.has("LateralDeltoid"));
      assert.ok(targets.has("PosteriorDeltoid"));
      assert.ok(targets.has("AnteriorDeltoid"));
    });

    it("excludes Omitted muscles from the target map", () => {
      const ctx = contextWith({ specialization: { Calf: "Omit" } });
      const targets = computeAllWeeklyTargets(ctx);
      assert.equal(targets.has("Calf"), false);
    });

    it("specialized muscles get a higher aim than maintenance muscles with the same landmark", () => {
      const ctx = contextWith({ specialization: { Chest: "Specialize", Back: "Maintenance" } });
      const targets = computeAllWeeklyTargets(ctx);
      const chest = targets.get("Chest");
      const back = targets.get("Back");
      assert.ok(chest && back);
      assert.ok(chest.aim > back.aim, "Specialized Chest aim should exceed Maintenance Back aim");
    });

    it("includes a muscle that appears only via a literature landmark (not a major muscle)", () => {
      const ctx = contextWith({
        specialization: {},
        literatureVolumeLandmarks: [
          { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
        ],
      });
      const targets = computeAllWeeklyTargets(ctx);
      assert.ok(targets.has("Calf"));
      const calf = targets.get("Calf");
      assert.equal(calf?.aim, 10); // Maintenance, non-conservative → MAV
    });
  });

  describe("availablePatternsByMuscle", () => {
    it("maps each muscle to the movement patterns of enabled lifts that hit it", () => {
      const ctx = contextWith();
      const patterns = availablePatternsByMuscle(ctx);
      // bench: HorizontalPress, primary Chest, secondary Tricep
      assert.ok(patterns.get("Chest")?.includes("HorizontalPress"));
      assert.ok(patterns.get("Tricep")?.includes("HorizontalPress")); // secondary
      // row: HorizontalPull, primary Back, secondary Bicep
      assert.ok(patterns.get("Back")?.includes("HorizontalPull"));
      assert.ok(patterns.get("Bicep")?.includes("HorizontalPull")); // secondary
    });

    it("returns an empty map when the user has no enabled lifts", () => {
      const ctx = contextWith({ provenLifts: [], availableLifts: [] });
      const patterns = availablePatternsByMuscle(ctx);
      assert.equal(patterns.size, 0);
    });

    it("deduplicates patterns across primary and secondary contributions", () => {
      const ctx = contextWith({
        provenLifts: [
          { id: "bench", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
          { id: "inc-press", movementPattern: "InclinePress", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
        ],
      });
      const patterns = availablePatternsByMuscle(ctx);
      const chest = patterns.get("Chest") ?? [];
      assert.equal(chest.filter((p) => p === "HorizontalPress").length, 1);
      assert.ok(chest.includes("InclinePress"));
    });
  });
});