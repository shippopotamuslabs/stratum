import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { planWeeklyVolume } from "./volumePlanner";
import { resolveSplit } from "./splitResolver";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
];

const LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
];

function contextFor(days: number) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days);
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Intermediate",
        sessionLengthMinutes: 75,
        weekdays: weekdays.join(","),
        trainingDaysPerWeek: days,
      },
      specialization: { Bicep: "Specialize", Tricep: "Specialize" },
      provenLifts: LIFT_CATALOG,
      availableLifts: [],
      literatureVolumeLandmarks: LANDMARKS,
    }),
  );
}

describe("splitResolver", () => {
  it("4-day profile resolves UL×2 with cycleLength 4", () => {
    const context = contextFor(4);
    const resolved = resolveSplit(context, planWeeklyVolume(context));
    assert.equal(resolved.cycleLength, 4);
    assert.equal(resolved.sessions.length, 4);
  });

  it("3 training days per week can yield a 6-session rolling cycle", () => {
    const context = contextFor(3);
    const resolved = resolveSplit(context, planWeeklyVolume(context));
    assert.ok(resolved.cycleLength >= 3);
    assert.equal(resolved.sessions.length, resolved.cycleLength);
  });

  it("every session has named slots", () => {
    const context = contextFor(5);
    const resolved = resolveSplit(context, planWeeklyVolume(context));
    for (const session of resolved.sessions) {
      assert.ok(session.name.length > 0);
      assert.ok(session.slots.length > 0);
    }
  });
});
