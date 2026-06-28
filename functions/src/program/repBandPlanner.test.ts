import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { repBandFor, repBandsForLifts } from "./repBandPlanner";

function liftCtx(overrides: Partial<{ id: string; name: string; movementPattern: string; primaryMuscles: string[]; secondaryMuscles: string[]; trainingInsights: Record<string, unknown>; proven: boolean }>) {
  return {
    id: overrides.id ?? "bench",
    name: overrides.name ?? "Bench",
    movementPattern: overrides.movementPattern ?? "HorizontalPush",
    primaryMuscles: overrides.primaryMuscles ?? ["Chest"],
    secondaryMuscles: overrides.secondaryMuscles ?? [],
    ...(overrides.trainingInsights ? { trainingInsights: overrides.trainingInsights } : {}),
    ...(overrides.proven != null ? { proven: overrides.proven } : {}),
  };
}

function ctxFor(trainingAge: string, lifts: unknown[]) {
  return parseGenerationContext(
    JSON.stringify({
      profile: { trainingAge, weekdays: "Mon,Wed,Fri,Sat", trainingDaysPerWeek: 4 },
      specialization: {},
      provenLifts: lifts,
      availableLifts: [],
      literatureVolumeLandmarks: [],
    }),
  );
}

describe("repBandPlanner", () => {
  describe("repBandFor — base bands", () => {
    it("Beginner compound: 6–10", () => {
      const ctx = ctxFor("Beginner", [liftCtx({ movementPattern: "HorizontalPush" })]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 10);
    });

    it("Beginner isolation: 10–15", () => {
      const ctx = ctxFor("Beginner", [liftCtx({ id: "curl", movementPattern: "BicepIso" })]);
      const band = repBandFor(ctx.liftsById.get("curl")!, ctx);
      assert.equal(band.repMin, 10);
      assert.equal(band.repMax, 15);
    });

    it("Intermediate+ compound: 6–12", () => {
      const ctx = ctxFor("Intermediate", [liftCtx({ movementPattern: "LegPush" })]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 12);
    });

    it("Intermediate+ isolation: 10–20", () => {
      const ctx = ctxFor("Advanced", [liftCtx({ id: "curl", movementPattern: "TricepIso" })]);
      const band = repBandFor(ctx.liftsById.get("curl")!, ctx);
      assert.equal(band.repMin, 10);
      assert.equal(band.repMax, 20);
    });
  });

  describe("repBandFor — fatigue decay adjustment", () => {
    it("steep decay (< -1.0) shifts the band down by 1; very steep (< -2.0) by 2", () => {
      const steepCtx = ctxFor("Intermediate", [
        liftCtx({ trainingInsights: { fatigueDecaySlope: -1.5, progressTrend: "Stagnant" } }),
      ]);
      const steep = repBandFor(steepCtx.liftsById.get("bench")!, steepCtx);
      // Base 6–12, shift down 1 → 5–11, clamp floor 5 → 5–11.
      assert.equal(steep.repMin, 5);
      assert.equal(steep.repMax, 11);

      const verySteepCtx = ctxFor("Intermediate", [
        liftCtx({ trainingInsights: { fatigueDecaySlope: -2.5, progressTrend: "Stagnant" } }),
      ]);
      const verySteep = repBandFor(verySteepCtx.liftsById.get("bench")!, verySteepCtx);
      // Base 6–12, shift down 2 → 5(6-1)–10, clamp floor 5 → 5–10.
      assert.equal(verySteep.repMin, 5);
      assert.equal(verySteep.repMax, 10);
    });

    it("flat decay (> -0.3) + Improving shifts the band up by 1", () => {
      const ctx = ctxFor("Intermediate", [
        liftCtx({ trainingInsights: { fatigueDecaySlope: -0.1, progressTrend: "Improving" } }),
      ]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      // Base 6–12, shift up 1 → 6–13, clamp ceiling 12 → 6–12.
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 12);
    });

    it("flat decay without Improving trend does not shift up", () => {
      const ctx = ctxFor("Intermediate", [
        liftCtx({ trainingInsights: { fatigueDecaySlope: -0.1, progressTrend: "Stagnant" } }),
      ]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 12);
    });

    it("moderate decay (-0.3..-1.0) leaves the base band unchanged", () => {
      const ctx = ctxFor("Intermediate", [
        liftCtx({ trainingInsights: { fatigueDecaySlope: -0.6, progressTrend: "Improving" } }),
      ]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 12);
    });

    it("no fatigue slope leaves the base band unchanged", () => {
      const ctx = ctxFor("Intermediate", [liftCtx({})]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.equal(band.repMin, 6);
      assert.equal(band.repMax, 12);
    });

    it("isolation ceiling clamps at 25 even with gentle decay + Improving", () => {
      const ctx = ctxFor("Advanced", [
        liftCtx({ id: "curl", movementPattern: "BicepIso", trainingInsights: { fatigueDecaySlope: 0.0, progressTrend: "Improving" } }),
      ]);
      const band = repBandFor(ctx.liftsById.get("curl")!, ctx);
      // Base 10–20, shift up 1 → 10–21, clamp ceiling 25 → 10–21.
      assert.equal(band.repMin, 10);
      assert.equal(band.repMax, 21);
    });

    it("repMin never exceeds repMax", () => {
      const ctx = ctxFor("Beginner", [liftCtx({ trainingInsights: { fatigueDecaySlope: -5.0 } })]);
      const band = repBandFor(ctx.liftsById.get("bench")!, ctx);
      assert.ok(band.repMin <= band.repMax);
    });
  });

  describe("repBandsForLifts", () => {
    it("returns a band per known lift id and skips unknown ids", () => {
      const ctx = ctxFor("Intermediate", [
        liftCtx({ id: "bench", movementPattern: "HorizontalPush" }),
        liftCtx({ id: "curl", movementPattern: "BicepIso" }),
      ]);
      const bands = repBandsForLifts(["bench", "curl", "phantom"], ctx);
      assert.ok(bands.has("bench"));
      assert.ok(bands.has("curl"));
      assert.equal(bands.has("phantom"), false);
    });
  });
});