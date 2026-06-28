import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { HttpsError } from "firebase-functions/v2/https";
import type OpenAI from "openai";
import {
  assertCanClaimGenerationSlot,
  cachedProgramFromMarker,
  generateProgramWithDeps,
  GENERATION_CALLABLE_TIMEOUT_MS,
  isStalePendingMarker,
  parseProgramGenerationPayload,
  type ChatCompletionLike,
  type GenerationMarkerSnapshot,
} from "./generateProgram";
import { programGenerationSessionId } from "../openrouterSession";
import { programBuilderOutputSchema } from "./programBuilderPrompt";
import type { ProgramNormalizationRequest } from "./programSchema";

const SESSION_ID = programGenerationSessionId("user-1");
const MODEL = "deepseek/deepseek-v4-pro";

/** A payload rich enough to drive the pipeline (4-day UL×2, enabled lifts, landmarks). */
const PAYLOAD = JSON.stringify({
  profile: {
    trainingAge: "Intermediate",
    sessionLengthMinutes: 75,
    weekdays: "Monday,Tuesday,Thursday,Friday",
    trainingDaysPerWeek: 4,
  },
  specialization: { Chest: "Specialize" },
  provenLifts: [
    { id: "bench", name: "Bench Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8 } },
    { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
    { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
    { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
    { id: "squat", name: "Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
    { id: "front-squat", name: "Front Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
    { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
    { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
    { id: "lat-raise", name: "Lateral Raise", movementPattern: "DeltIso", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
    { id: "curl", name: "Curl", movementPattern: "BicepIso", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
    { id: "tricep-ext", name: "Tricep Extension", movementPattern: "TricepIso", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
    { id: "calf-raise", name: "Calf Raise", movementPattern: "Calves", primaryMuscles: ["Calf"], secondaryMuscles: [] },
    { id: "seated-calf", name: "Seated Calf Raise", movementPattern: "Calves", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  ],
  availableLifts: [],
  literatureVolumeLandmarks: [
    { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedmrv: 20, estimatedMrv: 20, estimatedPv: 11 },
    { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
    { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
    { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedmrv: 16, estimatedMrv: 16, estimatedPv: 8 },
    { muscle: "Glute", estimatedMev: 4, estimatedmav: 8, estimatedMrv: 14, estimatedPv: 6 },
    { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
    { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedmrv: 14, estimatedMrv: 14, estimatedPv: 6 },
    { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
    { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  ],
});

const NORMALIZATION_REQUEST: ProgramNormalizationRequest = {
  trainingAge: "Intermediate",
  enabledLiftIds: new Set(["bench", "db-press", "row", "seated-row", "squat", "front-squat", "rdl", "gm", "lat-raise", "curl", "tricep-ext", "calf-raise", "seated-calf"]),
  movementPatternByLiftId: new Map([
    ["bench", "HorizontalPush"], ["db-press", "HorizontalPush"],
    ["row", "HorizontalPull"], ["seated-row", "HorizontalPull"],
    ["squat", "LegPush"], ["front-squat", "LegPush"],
    ["rdl", "HipHinge"], ["gm", "HipHinge"],
    ["lat-raise", "DeltIso"], ["curl", "BicepIso"], ["tricep-ext", "TricepIso"],
    ["calf-raise", "Calves"], ["seated-calf", "Calves"],
  ]),
};

/** A single set spec for [liftId]. */
function sets(liftId: string): unknown[] {
  void liftId;
  return [
    { position: 0, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: true },
    { position: 1, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: false },
    { position: 2, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: false },
  ];
}

/** A valid whole-program builder response: 4 workouts with sibling days varying their lifts. */
function validProgramJson(): string {
  return JSON.stringify({
    program: {
      name: "Upper/Lower",
      workouts: [
        { name: "Upper A", weekday: "Monday", position: 0, workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: sets("bench") },
          { liftId: "row", position: 1, workoutLiftSets: sets("row") },
        ] },
        { name: "Lower A", weekday: "Tuesday", position: 1, workoutLifts: [
          { liftId: "squat", position: 0, workoutLiftSets: sets("squat") },
          { liftId: "rdl", position: 1, workoutLiftSets: sets("rdl") },
        ] },
        { name: "Upper B", weekday: "Thursday", position: 2, workoutLifts: [
          { liftId: "db-press", position: 0, workoutLiftSets: sets("db-press") },
          { liftId: "seated-row", position: 1, workoutLiftSets: sets("seated-row") },
        ] },
        { name: "Lower B", weekday: "Friday", position: 3, workoutLifts: [
          { liftId: "front-squat", position: 0, workoutLiftSets: sets("front-squat") },
          { liftId: "gm", position: 1, workoutLiftSets: sets("gm") },
        ] },
      ],
    },
  });
}

/**
 * Fake client that routes by response_format schema name. "program_generation" responses come from
 * the queue (one per whole-program builder call); "split_planner" returns null; anything else null.
 */
function fakeClient(
  builderResponses: Array<string | null>,
): { client: ChatCompletionLike; builderCallCount: () => number } {
  let builderCalls = 0;
  const queue = [...builderResponses];
  const client: ChatCompletionLike = {
    chat: {
      completions: {
        create: async (params: OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming) => {
          const schemaName =
            (params.response_format as { json_schema?: { name?: string } } | undefined)?.json_schema?.name;
          if (schemaName === "program_generation") {
            builderCalls++;
            const content = queue.shift() ?? null;
            return { choices: [{ message: { content } }] } as OpenAI.Chat.Completions.ChatCompletion;
          }
          // split_planner or anything else: return null (4-day path doesn't call the planner).
          return { choices: [{ message: { content: null } }] } as OpenAI.Chat.Completions.ChatCompletion;
        },
      },
    },
  };
  return { client, builderCallCount: () => builderCalls };
}

describe("generateProgram", () => {
  describe("PROG-GEN-700 callable contract", () => {
    it("uses the program-generation session id helper", () => {
      assert.equal(programGenerationSessionId("user-1"), "stratum-program-generate:user-1");
    });

    it("the program-builder schema is strict and named program_generation (whole-program schema)", () => {
      const schema = programBuilderOutputSchema();
      assert.equal(schema.name, "program_generation");
      assert.equal(schema.strict, true);
      // progressionScheme is omitted from the builder schema (server assigns it post-pass).
      const liftProps = (
        schema.schema.properties.program.properties.workouts.items.properties.workoutLifts.items.properties
      ) as Record<string, unknown>;
      assert.ok(!("progressionScheme" in liftProps));
    });
  });

  describe("PROG-GEN-702b a valid program is returned from a single whole-program call", () => {
    it("returns a normalized program with 4 workouts from ONE builder call", async () => {
      const { client, builderCallCount } = fakeClient([validProgramJson()]);
      const result = await generateProgramWithDeps(
        client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID,
        { skipFinalValidation: true },
      ) as { name: string; workouts: unknown[] };
      assert.equal(builderCallCount(), 1, "whole-program path makes a single builder call");
      assert.ok(result.workouts.length === 4);
    });

    it("sibling days (Upper A/B, Lower A/B) have differing lift sets", async () => {
      const { client } = fakeClient([validProgramJson()]);
      const result = await generateProgramWithDeps(
        client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID,
        { skipFinalValidation: true },
      ) as { workouts: Array<{ name: string; workoutLifts?: Array<{ liftId?: string }> }> };
      const byName = new Map(result.workouts.map((w) => [w.name, new Set((w.workoutLifts ?? []).map((l) => l.liftId))]));
      const upperA = byName.get("Upper A");
      const upperB = byName.get("Upper B");
      assert.ok(upperA && upperB, "Upper A and Upper B should both be present");
      // Siblings must not be identical lift sets (A differs from B in at least one lift).
      assert.ok(
        [...upperA].some((id) => !upperB.has(id)) || [...upperB].some((id) => !upperA.has(id)),
        `Upper A ${[...upperA]} must differ from Upper B ${[...upperB]}`,
      );
    });

    it("no duplicate liftId appears within a single workout (dedup applied)", async () => {
      // Feed a program where Upper A repeats bench in two slots — repair must collapse it.
      const duped = JSON.stringify({
        program: {
          name: "Upper/Lower",
          workouts: [
            { name: "Upper A", weekday: "Monday", position: 0, workoutLifts: [
              { liftId: "bench", position: 0, workoutLiftSets: sets("bench") },
              { liftId: "bench", position: 1, workoutLiftSets: sets("bench") },
              { liftId: "row", position: 2, workoutLiftSets: sets("row") },
            ] },
            { name: "Lower A", weekday: "Tuesday", position: 1, workoutLifts: [
              { liftId: "squat", position: 0, workoutLiftSets: sets("squat") },
              { liftId: "rdl", position: 1, workoutLiftSets: sets("rdl") },
            ] },
            { name: "Upper B", weekday: "Thursday", position: 2, workoutLifts: [
              { liftId: "row", position: 0, workoutLiftSets: sets("row") },
              { liftId: "lat-raise", position: 1, workoutLiftSets: sets("lat-raise") },
            ] },
            { name: "Lower B", weekday: "Friday", position: 3, workoutLifts: [
              { liftId: "rdl", position: 0, workoutLiftSets: sets("rdl") },
              { liftId: "calf-raise", position: 1, workoutLiftSets: sets("calf-raise") },
            ] },
          ],
        },
      });
      const { client } = fakeClient([duped]);
      const result = await generateProgramWithDeps(
        client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID,
        { skipFinalValidation: true },
      ) as { workouts: Array<{ name: string; workoutLifts?: Array<{ liftId?: string }> }> };
      for (const workout of result.workouts) {
        const ids = (workout.workoutLifts ?? []).map((l) => l.liftId);
        assert.equal(new Set(ids).size, ids.length, `workout "${workout.name}" has duplicate liftIds: ${JSON.stringify(ids)}`);
      }
    });

    it("the whole-program builder returns invalid JSON → every day falls back deterministically", async () => {
      const { client } = fakeClient(["not json"]);
      const result = await generateProgramWithDeps(
        client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID,
        { skipFinalValidation: true },
      ) as { workouts: Array<{ workoutLifts?: unknown[] }> };
      assert.ok(result.workouts.length === 4);
      for (const workout of result.workouts) {
        assert.ok((workout.workoutLifts?.length ?? 0) > 0, "failed builder call should yield non-empty fallback workouts");
      }
    });

    it("the whole-program builder returns null → full deterministic fallback produces a valid program", async () => {
      const { client } = fakeClient([null]);
      const result = await generateProgramWithDeps(
        client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID,
        { skipFinalValidation: true },
      ) as { workouts: Array<{ workoutLifts?: unknown[] }> };
      assert.ok(result.workouts.length === 4);
      for (const workout of result.workouts) {
        assert.ok((workout.workoutLifts?.length ?? 0) > 0, "fallback workout should be non-empty");
      }
    });
  });

  describe("PROG-GEN-703 generation marker lifecycle", () => {
    const CACHED_PROGRAM = { name: "Cached Full Body", workouts: [] };

    it("parses payloads with populated responseProfile influence arrays", () => {
      const payload = JSON.stringify({
        profile: { trainingAge: "Beginner" },
        provenLifts: [],
        availableLifts: [],
        responseProfile: {
          epochsAnalyzed: 1,
          analysisWindowStart: "2025-01-01T00:00:00Z",
          analysisWindowEnd: "2025-06-01T00:00:00Z",
          volumeResponses: [
            { muscle: "Quad", anchorLiftId: "back-squat", correlationSlope: 0.8, confidenceScore: 0.8, evidence: "Proven" },
          ],
          liftEffectiveness: [],
          volumeLandmarks: [],
          informational: { volumeResponses: [], liftEffectiveness: [] },
        },
      });
      const request = parseProgramGenerationPayload(payload);
      assert.equal(request.trainingAge, "Beginner");
      assert.ok(request.enabledLiftIds.size === 0);
    });

    it("rejects invalid payload JSON before any Firestore slot is claimable", () => {
      assert.throws(
        () => parseProgramGenerationPayload("not json"),
        (err: unknown) => {
          assert.ok(err instanceof HttpsError);
          assert.equal(err.code, "invalid-argument");
          return true;
        },
      );
    });

    it("allows claiming when no marker exists", () => {
      assert.doesNotThrow(() => assertCanClaimGenerationSlot({ exists: false }));
    });

    it("allows reclaiming a completed marker that has no cached program", () => {
      assert.doesNotThrow(() =>
        assertCanClaimGenerationSlot({ exists: true, status: "completed" }),
      );
    });

    it("rejects claiming when generation completed with a cached program", () => {
      assert.throws(
        () =>
          assertCanClaimGenerationSlot({
            exists: true,
            status: "completed",
            program: CACHED_PROGRAM,
          }),
        (err: unknown) => {
          assert.ok(err instanceof HttpsError);
          assert.equal(err.code, "already-exists");
          return true;
        },
      );
    });

    it("returns a cached program for idempotent client retries", () => {
      const cached = cachedProgramFromMarker({
        exists: true,
        status: "completed",
        program: CACHED_PROGRAM,
      });
      assert.deepEqual(cached, CACHED_PROGRAM);
    });

    it("does not treat completed markers without a program cache as retrievable", () => {
      assert.equal(
        cachedProgramFromMarker({ exists: true, status: "completed" }),
        null,
      );
    });

    it("rejects claiming when generation is already in progress", () => {
      const now = Date.parse("2026-06-27T12:00:00.000Z");
      assert.throws(
        () =>
          assertCanClaimGenerationSlot(
            { exists: true, status: "pending", startedAtMs: now - 30_000 },
            now,
          ),
        (err: unknown) => {
          assert.ok(err instanceof HttpsError);
          assert.equal(err.code, "aborted");
          return true;
        },
      );
    });

    it("allows reclaiming a stale pending marker after the callable timeout", () => {
      const now = Date.parse("2026-06-27T12:00:00.000Z");
      const snapshot: GenerationMarkerSnapshot = {
        exists: true,
        status: "pending",
        startedAtMs: now - GENERATION_CALLABLE_TIMEOUT_MS - 1,
      };
      assert.ok(isStalePendingMarker(snapshot, now));
      assert.doesNotThrow(() => assertCanClaimGenerationSlot(snapshot, now));
    });
  });
});