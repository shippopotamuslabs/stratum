import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { LiftContext } from "./programPayloadContext";
import { liftCatalogQualityAdjustment } from "./liftCatalogQuality";

function lift(overrides: Partial<LiftContext> & Pick<LiftContext, "id" | "name" | "movementPattern">): LiftContext {
  return {
    movementPatterns: [overrides.movementPattern],
    primaryMuscles: [],
    secondaryMuscles: [],
    proven: true,
    ...overrides,
  };
}

describe("liftCatalogQuality", () => {
  it("demotes decline bench and behind-neck press vs flat/incline alternatives", () => {
    const decline = lift({
      id: "decline-barbell-bench-press",
      name: "Decline Barbell Bench Press",
      movementPattern: "HorizontalPress",
      primaryMuscles: ["Chest"],
      mainstream: true,
    });
    const flat = lift({
      id: "barbell-bench-press",
      name: "Barbell Bench Press",
      movementPattern: "HorizontalPress",
      primaryMuscles: ["Chest"],
      mainstream: true,
    });
    assert.ok(liftCatalogQualityAdjustment(decline) < liftCatalogQualityAdjustment(flat));

    const behindNeck = lift({
      id: "standing-barbell-press-behind-neck",
      name: "Standing Barbell Press Behind Neck",
      movementPattern: "VerticalPress",
      primaryMuscles: ["AnteriorDeltoid"],
      mainstream: false,
    });
    const machinePress = lift({
      id: "machine-shoulder-military-press",
      name: "Machine Shoulder (Military) Press",
      movementPattern: "VerticalPress",
      primaryMuscles: ["AnteriorDeltoid"],
      mainstream: true,
    });
    assert.ok(liftCatalogQualityAdjustment(behindNeck) < liftCatalogQualityAdjustment(machinePress));
  });

  it("prefers cable/skull tricep isolation over dip machine", () => {
    const dipMachine = lift({
      id: "dip-machine",
      name: "Dip Machine",
      movementPattern: "ElbowExtension",
      primaryMuscles: ["Tricep"],
      mainstream: false,
    });
    const skull = lift({
      id: "ez-bar-skull-crusher",
      name: "EZ-Bar Skull Crusher",
      movementPattern: "ElbowExtension",
      primaryMuscles: ["Tricep"],
      mainstream: true,
    });
    const tricepSlot = { slotId: "tricep", role: "isolation" as const, pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" as const };
    assert.ok(
      liftCatalogQualityAdjustment(skull, tricepSlot) > liftCatalogQualityAdjustment(dipMachine, tricepSlot),
    );
  });

  it("demotes one-leg barbell squat vs front squat", () => {
    const oneLeg = lift({
      id: "one-leg-barbell-squat",
      name: "One Leg Barbell Squat",
      movementPattern: "SquatPattern",
      primaryMuscles: ["Quad"],
      mainstream: false,
    });
    const front = lift({
      id: "barbell-front-squat",
      name: "Barbell Front Squat",
      movementPattern: "SquatPattern",
      primaryMuscles: ["Quad"],
      mainstream: true,
    });
    assert.ok(liftCatalogQualityAdjustment(oneLeg) < liftCatalogQualityAdjustment(front));
  });
});
