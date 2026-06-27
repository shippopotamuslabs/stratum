import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import admin from "firebase-admin";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  programGenerationSessionId,
} from "../openrouterSession";
import {
  PROGRAM_GENERATION_SYSTEM_PROMPT,
} from "./programGenerationSystemPrompt";
import {
  parseAndNormalizeProgram,
  programJsonSchema,
  type ProgramNormalizationRequest,
} from "./programSchema";

export type GenerationMarkerStatus = "pending" | "completed";

/** Firestore snapshot shape used by [assertCanClaimGenerationSlot] (unit-testable). */
export interface GenerationMarkerSnapshot {
  exists: boolean;
  status?: GenerationMarkerStatus;
}

/**
 * `generateProgram` — one-shot LLM program generation (OS-7). Sends the assembled onboarding payload
 * to OpenRouter under a strict JSON schema, then deterministically validates + normalizes the
 * response (assign progressionScheme from trainingAge, clamp RIR, enforce straight sets) before
 * returning the normalized `Program` document. Mirrors `inferImportEquipment` for auth/secrets/
 * routing.
 *
 * Abuse boundary: exactly one bounded generation per onboarding completion (per-uid). No follow-up
 * turns, no open chat.
 */
export const generateProgram = onCall(
  {
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"],
    enforceAppCheck: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }

    const data = request.data as { payload?: string };
    const payload = data.payload;
    if (typeof payload !== "string" || payload.trim().length === 0) {
      throw new HttpsError("invalid-argument", "payload must be a non-empty string.");
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    if (!apiKey) {
      throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
    }
    const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";

    // Validate payload JSON before touching Firestore so malformed input never burns a slot.
    const normalizationRequest = parseProgramGenerationPayload(payload);
    const uid = request.auth.uid;

    await claimGenerationSlot(uid);
    try {
      const sessionId = programGenerationSessionId(uid);
      const client = createOpenRouterClient(apiKey, sessionId);
      const program = await generateProgramWithDeps(
        client,
        model,
        payload,
        normalizationRequest,
        sessionId,
      );
      await markGenerationCompleted(uid);
      return { program };
    } catch (error) {
      await releaseGenerationSlot(uid);
      throw error;
    }
  },
);

/** OpenAI client seam used by [generateProgramWithDeps] (and its tests). */
export interface ChatCompletionLike {
  chat: {
    completions: {
      create: (
        params: OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming,
      ) => Promise<OpenAI.Chat.Completions.ChatCompletion>;
    };
  };
}

/**
 * Core generation loop with injected deps — the unit-testable seam (mirrors
 * `runImportAgentSessionWithDeps`). One retry on schema/parse/normalize failure; a second failure
 * surfaces as `internal`. Returns the normalized program document (no ids — the client assigns
 * UUIDs at parse time, D1).
 */
export async function generateProgramWithDeps(
  client: ChatCompletionLike,
  model: string,
  payload: string,
  normalizationRequest: ProgramNormalizationRequest,
  sessionId: string,
): Promise<unknown> {
  const extras = buildOpenRouterChatCompletionExtras(sessionId, {
    requireParameters: true,
  });
  for (let attempt = 0; attempt < 2; attempt++) {
    const completion = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: PROGRAM_GENERATION_SYSTEM_PROMPT },
        { role: "user", content: payload },
      ],
      response_format: {
        type: "json_schema",
        json_schema: programJsonSchema(),
      },
      ...extras,
    } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
    const content = completion.choices[0]?.message?.content;
    if (!content) {
      if (attempt === 1) {
        throw new HttpsError("internal", "Empty model response.");
      }
      continue;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(content);
    } catch {
      if (attempt === 1) {
        throw new HttpsError("internal", "Model returned invalid JSON.");
      }
      continue;
    }
    try {
      return parseAndNormalizeProgram(parsed, normalizationRequest);
    } catch (error) {
      if (attempt === 1) {
        const message = error instanceof Error ? error.message : "Normalization failed.";
        throw new HttpsError("invalid-argument", message);
      }
    }
  }
  throw new HttpsError("internal", "Generation failed after retry.");
}

/**
 * Extracts trainingAge + enabled lift ids + movement patterns from the payload JSON for the
 * deterministic post-pass. The payload is the JSON string built by `ProgramGenerationPromptBuilder`:
 * `{"profile":{"trainingAge":"..."},"provenLifts":[{"id","movementPattern"},...],"availableLifts":[...]}`.
 */
export function parseProgramGenerationPayload(payload: string): ProgramNormalizationRequest {
  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    throw new HttpsError("invalid-argument", "payload must be valid JSON.");
  }
  const body = parsed as {
    profile?: { trainingAge?: string };
    provenLifts?: Array<{ id?: string; movementPattern?: string }>;
    availableLifts?: Array<{ id?: string; movementPattern?: string }>;
  };
  const trainingAge = body.profile?.trainingAge ?? "";
  const enabledLiftIds = new Set<string>();
  const movementPatternByLiftId = new Map<string, string>();
  for (const lift of [...(body.provenLifts ?? []), ...(body.availableLifts ?? [])]) {
    const id = typeof lift?.id === "string" ? lift.id : null;
    if (!id) continue;
    enabledLiftIds.add(id);
    if (typeof lift.movementPattern === "string") {
      movementPatternByLiftId.set(id, lift.movementPattern);
    }
  }
  return { trainingAge, enabledLiftIds, movementPatternByLiftId };
}

/**
 * Guards the Firestore generation slot before claiming `pending`. Rejects completed generations
 * (`already-exists`) and in-flight duplicates (`aborted`). Missing or released slots are claimable.
 */
export function assertCanClaimGenerationSlot(snapshot: GenerationMarkerSnapshot): void {
  if (!snapshot.exists) return;
  if (snapshot.status === "completed") {
    throw new HttpsError(
      "already-exists",
      "Program generation already completed for this user.",
    );
  }
  if (snapshot.status === "pending") {
    throw new HttpsError("aborted", "Program generation already in progress.");
  }
}

function generationMarkerRef(uid: string) {
  return admin.firestore().doc(`users/${uid}/programs_generation/latest`);
}

async function claimGenerationSlot(uid: string): Promise<void> {
  const generationRef = generationMarkerRef(uid);
  try {
    await admin.firestore().runTransaction(async (txn) => {
      const doc = await txn.get(generationRef);
      assertCanClaimGenerationSlot({
        exists: doc.exists,
        status: doc.data()?.status as GenerationMarkerStatus | undefined,
      });
      txn.set(generationRef, {
        status: "pending",
        startedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("internal", "Rate-limit check failed.");
  }
}

async function markGenerationCompleted(uid: string): Promise<void> {
  const generationRef = generationMarkerRef(uid);
  await admin.firestore().runTransaction(async (txn) => {
    const doc = await txn.get(generationRef);
    if (!doc.exists || doc.data()?.status !== "pending") {
      throw new HttpsError("internal", "Generation state lost.");
    }
    txn.set(generationRef, {
      status: "completed",
      generatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
}

/** Deletes a `pending` marker so a failed generation can be retried. No-op when already completed. */
async function releaseGenerationSlot(uid: string): Promise<void> {
  const generationRef = generationMarkerRef(uid);
  try {
    await admin.firestore().runTransaction(async (txn) => {
      const doc = await txn.get(generationRef);
      if (doc.exists && doc.data()?.status === "pending") {
        txn.delete(generationRef);
      }
    });
  } catch {
    // Best-effort — swallow release errors so the original failure surfaces to the client.
  }
}
