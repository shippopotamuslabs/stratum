import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { buildProgramWithSelections } from "./slotBasedProgramBuilder";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "inc-press", name: "Incline Press", movementPattern: "InclinePress", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "pullup", name: "Pullup", movementPattern: "VerticalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "leg-ext", name: "Leg Extension", movementPattern: "KneeExtension", primaryMuscles: ["Quad"], secondaryMuscles: [] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "leg-curl", name: "Leg Curl", movementPattern: "KneeFlexion", primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "ShoulderAbduction", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "ohp", name: "Overhead Press", movementPattern: "VerticalPress", primaryMuscles: ["AnteriorDeltoid"], secondaryMuscles: ["Tricep"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
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
  { muscle: "AnteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
];

function contextFor(days: number) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days);
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Advanced",
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

describe("slotBasedProgramBuilder", () => {
  it("4-day advanced arm specialization produces full cycle with null weekdays", () => {
    const { program } = buildProgramWithSelections(contextFor(4));
    assert.equal(program.cycleLength, 4);
    assert.equal(program.workouts.length, 4);
    for (const w of program.workouts) {
      assert.equal(w.weekday, null);
      assert.ok((w.workoutLifts?.length ?? 0) >= 3, `${w.name} should have enough exercises to fill a session`);
    }
  });

  it("lower days include at most one hip hinge compound", () => {
    const { program } = buildProgramWithSelections(contextFor(4));
    const hingeIds = new Set(["rdl", "gm"]);
    for (const w of program.workouts.filter((x) => (x.name ?? "").toLowerCase().includes("lower"))) {
      const hinges = (w.workoutLifts ?? []).filter((l) => hingeIds.has(l.liftId ?? ""));
      assert.ok(hinges.length <= 1, `${w.name} has ${hinges.length} hip hinges`);
    }
  });

  it("each Double lift has exactly one top set on the first set", () => {
    const { program } = buildProgramWithSelections(contextFor(4));
    for (const w of program.workouts) {
      for (const lift of w.workoutLifts ?? []) {
        const sets = (lift.workoutLiftSets ?? []) as Array<{ isTopSet?: boolean }>;
        const topSets = sets.filter((s) => s.isTopSet);
        assert.equal(topSets.length, 1, `${lift.liftId} in ${w.name}`);
      }
    }
  });
});
