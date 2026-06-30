import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { planWeeklyVolume } from "./volumePlanner";

const LIFTS = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
];

function contextFor(overrides: Record<string, unknown> = {}) {
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Intermediate",
        sessionLengthMinutes: 75,
        weekdays: "Monday,Tuesday,Thursday,Friday",
        trainingDaysPerWeek: 4,
        ...((overrides.profile as object) ?? {}),
      },
      specialization: { Bicep: "Specialize", Chest: "Maintenance", ...(overrides.specialization as object) },
      provenLifts: LIFTS,
      availableLifts: [],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
        { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
        { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
      ],
      ...overrides,
    }),
  );
}

describe("volumePlanner", () => {
  it("specialized muscles get higher weekly aim than maintenance for the same muscle landmarks", () => {
    const specializedCtx = contextFor({ specialization: { Bicep: "Specialize" } });
    const maintenanceCtx = contextFor({ specialization: { Bicep: "Maintenance" } });
    const specialized = planWeeklyVolume(specializedCtx).get("Bicep");
    const maintenance = planWeeklyVolume(maintenanceCtx).get("Bicep");
    assert.ok(specialized);
    assert.ok(maintenance);
    assert.ok(specialized.aim > maintenance.aim);
  });

  it("conservative return-to-training profile uses PV for maintenance muscles", () => {
    const targets = planWeeklyVolume(
      contextFor({
        profile: { lastTrained: "MoreThanAYearAgo" },
        specialization: { Bicep: "Maintenance", Tricep: "Maintenance", Chest: "Maintenance" },
      }),
    );
    const chest = targets.get("Chest");
    assert.ok(chest);
    assert.equal(chest.aim, 11);
  });

  it("arm specialization does not reduce leg volume on separate lower days", () => {
    const armSpec = planWeeklyVolume(contextFor({ specialization: { Bicep: "Specialize", Tricep: "Specialize" } }));
    const neutral = planWeeklyVolume(
      contextFor({ specialization: { Bicep: "Maintenance", Tricep: "Maintenance" } }),
    );
    assert.equal(armSpec.get("Quad")?.aim, neutral.get("Quad")?.aim);
    assert.equal(armSpec.get("Hamstring")?.aim, neutral.get("Hamstring")?.aim);
    assert.ok((armSpec.get("Bicep")?.aim ?? 0) > (neutral.get("Bicep")?.aim ?? 0));
  });

  it("arm specialization keeps full weekly back/chest targets; per-session cap is separate", () => {
    const armSpec = planWeeklyVolume(contextFor({ specialization: { Bicep: "Specialize", Tricep: "Specialize" } }));
    const neutral = planWeeklyVolume(contextFor({ specialization: { Bicep: "Maintenance", Tricep: "Maintenance" } }));
    assert.equal(armSpec.get("Back")?.aim, 14);
    assert.equal(armSpec.get("Chest")?.aim, 14);
    assert.equal(armSpec.get("Back")?.aim, neutral.get("Back")?.aim);
    assert.equal(armSpec.get("Chest")?.aim, neutral.get("Chest")?.aim);
    assert.ok((armSpec.get("Bicep")?.aim ?? 0) > (neutral.get("Bicep")?.aim ?? 0));
  });
});
