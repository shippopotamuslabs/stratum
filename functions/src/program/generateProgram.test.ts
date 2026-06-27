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
import { programJsonSchema } from "./programSchema";
import type { ProgramNormalizationRequest } from "./programSchema";

const SESSION_ID = programGenerationSessionId("user-1");
const MODEL = "deepseek/deepseek-v4-pro";
const PAYLOAD = '{"profile":{"trainingAge":"Beginner"}}';

const NORMALIZATION_REQUEST: ProgramNormalizationRequest = {
  trainingAge: "Beginner",
  enabledLiftIds: new Set(["back-squat"]),
  movementPatternByLiftId: new Map([["back-squat", "LegPush"]]),
};

const FIXTURE_A_JSON = JSON.stringify({
  program: {
    name: "Full Body",
    workouts: [
      {
        name: "Session 1",
        weekday: "Monday",
        position: 0,
        workoutLifts: [
          {
            liftId: "back-squat",
            position: 0,
            workoutLiftSets: [
              { position: 0, setType: "Standard", repMin: 5, repMax: 5, rirGoal: 9, isTopSet: true },
            ],
          },
        ],
      },
    ],
  },
});

/** Builds a fake OpenAI-like client whose `create` returns completions from a queue. */
function fakeClient(
  contents: Array<string | null>,
): { client: ChatCompletionLike; createCalls: () => number } {
  let createCalls = 0;
  const queue = [...contents];
  const client: ChatCompletionLike = {
    chat: {
      completions: {
        create: async () => {
          const content = queue.shift() ?? null;
          createCalls++;
          return {
            choices: [{ message: { content } }],
          } as OpenAI.Chat.Completions.ChatCompletion;
        },
      },
    },
  };
  return { client, createCalls: () => createCalls };
}

const CACHED_PROGRAM = { name: "Cached Full Body", workouts: [] };

describe("generateProgram", () => {
  describe("PROG-GEN-700 callable contract", () => {
    it("uses the program-generation session id helper and a strict json_schema response_format", () => {
      // The session id helper exists and is namespaced for program generation.
      assert.equal(programGenerationSessionId("user-1"), "stratum-program-generate:user-1");
      // The schema is strict and named for program generation.
      const schema = programJsonSchema();
      assert.equal(schema.name, "program_generation");
      assert.equal(schema.strict, true);
      // progressionScheme is omitted from the schema (the server assigns it post-pass).
      const liftProps = (
        schema.schema as {
          properties: {
            program: {
              properties: {
                workouts: {
                  items: {
                    properties: {
                      workoutLifts: { items: { properties: Record<string, unknown> } };
                    };
                  };
                };
              };
            };
          };
        }
      ).properties.program.properties.workouts.items.properties.workoutLifts.items.properties;
      assert.ok(!("progressionScheme" in liftProps));
    });
  });

  describe("PROG-GEN-702 retry once on schema failure then fail with internal", () => {
    it("calls the model exactly twice and rejects when both attempts return invalid JSON", async () => {
      const { client, createCalls } = fakeClient(["not json", "still not json"]);
      // After two invalid-JSON attempts the seam throws an HttpsError with code "internal".
      await assert.rejects(
        () => generateProgramWithDeps(client, MODEL, PAYLOAD, NORMALIZATION_REQUEST, SESSION_ID),
        (err: unknown) => {
          // HttpsError carries `code` and `message`; assert the code is internal OR the message
          // names the invalid-JSON failure.
          const code = (err as { code?: string }).code;
          const message = err instanceof Error ? err.message : String(err);
          return code === "internal" || /invalid JSON/i.test(message);
        },
      );
      assert.equal(createCalls(), 2);
    });
  });

  describe("PROG-GEN-702b retry succeeds when the second attempt is valid", () => {
    it("calls the model exactly twice and returns the normalized program on the valid second attempt", async () => {
      const { client, createCalls } = fakeClient(["not json", FIXTURE_A_JSON]);
      // The first attempt returns invalid JSON (retry); the second returns a valid payload that
      // normalizes successfully — the seam returns the program document.
      const result = await generateProgramWithDeps(
        client,
        MODEL,
        PAYLOAD,
        NORMALIZATION_REQUEST,
        SESSION_ID,
      );
      assert.equal(createCalls(), 2);
      assert.ok(typeof result === "object" && result !== null);
      assert.equal((result as { name?: string }).name, "Full Body");
    });
  });

  describe("PROG-GEN-703 generation marker lifecycle", () => {
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
            {
              muscle: "Quad",
              anchorLiftId: "back-squat",
              correlationSlope: 0.8,
              confidenceScore: 0.8,
              evidence: "Proven",
            },
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
            {
              exists: true,
              status: "pending",
              startedAtMs: now - 30_000,
            },
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
