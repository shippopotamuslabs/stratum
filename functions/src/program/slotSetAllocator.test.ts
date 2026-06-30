import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { planWeeklyVolume } from "./volumePlanner";
import { resolveSplit } from "./splitResolver";
import { allocateSlotSetCounts } from "./slotSetAllocator";
import { SESSION_MUSCLE_SET_CAP } from "./rpReferenceTables";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  { id: "leg-ext", name: "Leg Extension", movementPattern: "KneeExtension", primaryMuscles: ["Quad"], secondaryMuscles: [] },
  { id: "leg-curl", name: "Leg Curl", movementPattern: "KneeFlexion", primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "ShoulderAbduction", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
];

const LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
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

function primaryMuscleCreditsInSession(
  plan: ReturnType<typeof allocateSlotSetCounts>[number],
  muscle: string,
): number {
  return plan.filledSlots
    .filter((s) => s.primaryMuscle === muscle)
    .reduce((sum, s) => sum + s.setCount, 0);
}

describe("slotSetAllocator", () => {
  it("keeps per-session primary-muscle sets at or below SESSION_MUSCLE_SET_CAP", () => {
    const context = contextFor(4);
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    for (const plan of plans) {
      const muscles = new Set(plan.filledSlots.map((s) => s.primaryMuscle));
      for (const muscle of muscles) {
        assert.ok(
          primaryMuscleCreditsInSession(plan, muscle) <= SESSION_MUSCLE_SET_CAP,
          `${plan.name} exceeds cap for ${muscle}`,
        );
      }
    }
  });

  it("concentrates extra sets on specialized arm muscles", () => {
    const context = contextFor(4);
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    const bicepSets = plans.reduce((sum, p) => sum + primaryMuscleCreditsInSession(p, "Bicep"), 0);
    const tricepSets = plans.reduce((sum, p) => sum + primaryMuscleCreditsInSession(p, "Tricep"), 0);
    assert.ok(bicepSets >= targets.get("Bicep")!.aim * 0.4);
    assert.ok(tricepSets >= targets.get("Tricep")!.aim * 0.4);
  });

  it("no muscle exceeds 8 sets per workout on arm specialization 4-day UL", () => {
    const context = contextFor(4);
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    for (const plan of plans) {
      const muscles = new Set(plan.filledSlots.map((s) => s.primaryMuscle));
      for (const muscle of muscles) {
        assert.ok(
          primaryMuscleCreditsInSession(plan, muscle) <= SESSION_MUSCLE_SET_CAP,
          `${plan.name} ${muscle} exceeds ${SESSION_MUSCLE_SET_CAP} sets/session`,
        );
      }
    }
  });

  it("includes calf slot on short sessions when calf is specialized", () => {
    const weekdays = ["Monday", "Tuesday", "Wednesday"];
    const context = parseGenerationContext(
      JSON.stringify({
        profile: {
          trainingAge: "Advanced",
          sessionLengthMinutes: 30,
          weekdays: weekdays.join(","),
          trainingDaysPerWeek: 3,
        },
        specialization: { Calf: "Specialize", Chest: "Specialize" },
        provenLifts: LIFT_CATALOG,
        availableLifts: [],
        literatureVolumeLandmarks: LANDMARKS,
      }),
    );
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    const calfSets = plans.reduce(
      (sum, p) => sum + primaryMuscleCreditsInSession(p, "Calf"),
      0,
    );
    assert.ok(calfSets > 0, "calf specialization should allocate calf sets even on 30m sessions");
  });

  it("balances push and pull on full body instead of stacking all press sets", () => {
    const context = contextFor(3);
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    const fullBody = plans.find((p) => p.sessionType === "FullBody");
    assert.ok(fullBody, "expected a full body session on 3-day split");
    const press = fullBody!.filledSlots.find((s) => s.pattern === "HorizontalPress");
    const pull = fullBody!.filledSlots.find((s) => s.pattern === "HorizontalPull");
    assert.ok(press && pull, "full body should have press and pull slots");
    if (press.setCount >= 3) {
      assert.ok(pull.setCount > 0, "full body should not leave pull at zero when press is stacked");
      assert.ok(press.setCount <= 4, "press sets should respect compound per-slot cap");
    }
  });

  it("5-day 60m arm spec: upper push and pull keep compound work alongside arm isolation", () => {
    const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
    const context = parseGenerationContext(
      JSON.stringify({
        profile: {
          trainingAge: "Advanced",
          sessionLengthMinutes: 60,
          weekdays: weekdays.join(","),
          trainingDaysPerWeek: 5,
        },
        specialization: { Bicep: "Specialize", Tricep: "Specialize" },
        provenLifts: LIFT_CATALOG,
        availableLifts: [],
        literatureVolumeLandmarks: LANDMARKS,
      }),
    );
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);

    const push = plans.find((p) => p.name === "Upper Push A");
    assert.ok(push);
    const pushPress = push!.filledSlots.find((s) => s.pattern === "HorizontalPress");
    const pushTricep = push!.filledSlots.find((s) => s.primaryMuscle === "Tricep");
    assert.ok(pushPress && pushPress.setCount >= 3, "upper push should include flat/m horizontal press sets");
    assert.ok(pushTricep && pushTricep.setCount >= 2, "upper push should still include direct tricep work");
    assert.ok(
      push!.filledSlots.filter((s) => s.setCount > 0).length >= 4,
      "upper push should fill multiple slots, not just arms + one press",
    );

    const pull = plans.find((p) => p.name === "Upper Pull A");
    assert.ok(pull);
    const pullRow = pull!.filledSlots.find(
      (s) => s.pattern === "VerticalPull" || s.pattern === "HorizontalPull",
    );
    const pullBicep = pull!.filledSlots.find((s) => s.primaryMuscle === "Bicep");
    assert.ok(pullRow && pullRow.setCount >= 3, "upper pull should include row or pulldown work");
    assert.ok(pullBicep && pullBicep.setCount >= 2, "upper pull should still include direct bicep work");
  });

  it("5-day 60m arm spec: lower days stay balanced and use standard session volume", () => {
    const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
    const context = parseGenerationContext(
      JSON.stringify({
        profile: {
          trainingAge: "Advanced",
          sessionLengthMinutes: 60,
          weekdays: weekdays.join(","),
          trainingDaysPerWeek: 5,
        },
        specialization: { Bicep: "Specialize", Tricep: "Specialize" },
        provenLifts: LIFT_CATALOG,
        availableLifts: [],
        literatureVolumeLandmarks: LANDMARKS,
      }),
    );
    const targets = planWeeklyVolume(context);
    const resolved = resolveSplit(context, targets);
    const plans = allocateSlotSetCounts(resolved.sessions, targets, context);
    const lowerPlans = plans.filter((p) => p.sessionType === "Lower");
    assert.equal(lowerPlans.length, 2);

    const totals = lowerPlans.map((p) => p.filledSlots.reduce((s, sl) => s + sl.setCount, 0));
    assert.ok(
      Math.max(...totals) - Math.min(...totals) <= 3,
      `lower days should stay balanced (totals ${totals.join(" vs ")})`,
    );
    assert.equal(targets.get("Quad")?.aim, 14, "arm spec must not reduce quad weekly target");
    assert.equal(targets.get("Hamstring")?.aim, 10, "arm spec must not reduce hamstring weekly target");
    for (const total of totals) {
      assert.ok(total >= 15, `each lower day should use most of a 60m session (got ${total} sets)`);
    }
  });
});
