import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { RP_REP_RIR_BANDS } from "./rpReferenceTables";
import { classifyLiftRepProfile, repRirForSlot } from "./repRirPlanner";
import type { LiftContext } from "./programPayloadContext";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

function slot(partial: Partial<WorkoutSlotTemplate>): WorkoutSlotTemplate {
  return {
    pattern: "SquatPattern",
    primaryMuscle: "Quad",
    role: "anchor",
    repProfile: "lowerFreeWeightCompound",
    ...partial,
  } as WorkoutSlotTemplate;
}

function lift(partial: Partial<LiftContext>): LiftContext {
  return {
    id: "squat",
    name: "Barbell Squat",
    movementPattern: "SquatPattern",
    movementPatterns: ["SquatPattern"],
    primaryMuscles: ["Quad"],
    secondaryMuscles: ["Glute"],
    proven: true,
    ...partial,
  } as LiftContext;
}

describe("repRirPlanner", () => {
  it("TABLE 7.7 lower free-weight compound band for barbell squat", () => {
    const plan = repRirForSlot(slot({ repProfile: "lowerFreeWeightCompound" }), lift({}));
    const band = RP_REP_RIR_BANDS.lowerFreeWeightCompound;
    assert.equal(plan.repMin, band.repMin);
    assert.equal(plan.repMax, band.repMax);
    assert.equal(plan.topSetRir, band.topSetRir);
  });

  it("machine squat uses slot rep profile not free-weight override", () => {
    const profile = classifyLiftRepProfile(
      slot({ repProfile: "lowerMachineOrUpperPress" }),
      lift({ name: "Hack Squat Machine", movementPattern: "SquatPattern" }),
    );
    assert.equal(profile, "lowerMachineOrUpperPress");
  });

  it("isolation slots use isolation band", () => {
    const plan = repRirForSlot(
      slot({ repProfile: "isolation", pattern: "ElbowFlexion", primaryMuscle: "Bicep", role: "isolation" }),
      lift({ name: "Cable Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"] }),
    );
    assert.equal(plan.repMin, RP_REP_RIR_BANDS.isolation.repMin);
    assert.equal(plan.topSetRir, RP_REP_RIR_BANDS.isolation.topSetRir);
  });
});
