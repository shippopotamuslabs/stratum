import { logger } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import admin from "firebase-admin";
import {
  createOpenRouterClient,
  programGenerationSessionId,
} from "../openrouterSession";
import {
  parseGenerationContext,
  toNormalizationRequest,
} from "./programPayloadContext";
import {
  writeProgramGenerationProgress,
  type ProgramGenerationMarkerRef,
} from "./programGenerationProgress";
import {
  type ProgramNormalizationRequest,
} from "./programSchema";
import {
  generateProgramPipeline,
  type ChatCompletionLike,
} from "./generateProgramPipeline";

// Re-export so the existing test imports of ChatCompletionLike keep working.
export type { ChatCompletionLike };

export type GenerationMarkerStatus = "pending" | "completed";

/** Matches [generateProgram] Cloud Function and Android client callable timeouts. */
export const GENERATION_CALLABLE_TIMEOUT_MS = 600_000;

/** Firestore snapshot shape used by generation-slot helpers (unit-testable). */
export interface GenerationMarkerSnapshot {
  exists: boolean;
  status?: GenerationMarkerStatus;
  program?: unknown;
  startedAtMs?: number | null;
}

/**
 * `generateProgram` — one-shot LLM program generation (OS-7 v2). Runs the 3-phase pipeline
 * (deterministic precompute → bounded LLM per workout → deterministic integration) via
 * [generateProgramPipeline]. The pipeline always returns a valid program: per-workout failures
 * fall through to the deterministic fallback; a global re-gen is attempted once if final
 * validation fails; if that also fails, every workout is rebuilt from the deterministic fallback.
 *
 * OpenRouter routing for LLM calls is configured in the pipeline via
 * `buildProgramGenerationChatCompletionExtras`: sort by price, then deprioritize providers below
 * 50 tps (p90) at the same price tier.
 *
 * Abuse boundary: one in-flight generation per uid (concurrent duplicates abort). Regeneration
 * overwrites a prior completed marker. No follow-up turns, no open chat.
 */
export const generateProgram = onCall(
  {
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL", "OPENROUTER_PROGRAM_GENERATE_MODEL"],
    enforceAppCheck: true,
    // OpenRouter structured generation can exceed the 60s default; match the Android client timeout.
    timeoutSeconds: 600,
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
    const model = process.env.OPENROUTER_PROGRAM_GENERATE_MODEL ?? process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";

    // Validate payload JSON before touching Firestore so malformed input never burns a slot.
    const normalizationRequest = parseProgramGenerationPayload(payload);
    const uid = request.auth.uid;

    try {
      await claimGenerationSlot(uid);
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", "Rate-limit check failed.");
    }
    try {
      const markerRef = generationMarkerRef(uid);
      await writeProgramGenerationProgress(
        markerRef,
        "reviewing_profile",
        "Reviewing your training profile…",
      );
      const sessionId = programGenerationSessionId(uid);
      const client = createOpenRouterClient(apiKey, sessionId);
      const program = await generateProgramWithDeps(
        client,
        model,
        payload,
        normalizationRequest,
        sessionId,
        { markerRef },
      );
      await markGenerationCompleted(uid, program);
      return { program };
    } catch (error) {
      await releaseGenerationSlot(uid);
      throw error;
    }
  },
);

/**
 * Core generation entrypoint with injected deps. Delegates to [generateProgramPipeline] (the v2
 * 3-phase pipeline). Kept as a thin wrapper so the existing test seam (client + model + payload
 * + sessionId + options) and the callable's progress writes stay unchanged.
 *
 * The v1 tool phase + 6-attempt JSON loop are deleted; the pipeline owns all retry/fallback logic.
 */
export async function generateProgramWithDeps(
  client: ChatCompletionLike,
  model: string,
  payload: string,
  normalizationRequest: ProgramNormalizationRequest,
  sessionId: string,
  options?: {
    skipSplitPlanner?: boolean;
    skipFinalValidation?: boolean;
    skipLiftSwap?: boolean;
    markerRef?: ProgramGenerationMarkerRef;
  },
): Promise<unknown> {
  const markerRef = options?.markerRef;
  return generateProgramPipeline(client, model, payload, sessionId, normalizationRequest, {
    skipSplitPlanner: options?.skipSplitPlanner,
    skipFinalValidation: options?.skipFinalValidation,
    skipLiftSwap: options?.skipLiftSwap,
    progress: async (phase) => {
      if (!markerRef) return;
      const message =
        phase === "split_planning" ? "Planning your weekly split…" :
        phase === "building_workouts" ? "Building your workouts…" :
        "Putting it together…";
      await writeProgramGenerationProgress(markerRef, phase, message);
    },
  });
}

/**
 * Extracts trainingAge + enabled lift ids + movement patterns from the payload JSON for the
 * deterministic post-pass. The payload is the JSON string built by `ProgramGenerationPromptBuilder`:
 * `{"profile":{"trainingAge":"..."},"provenLifts":[{"id","movementPattern"},...],"availableLifts":[...]}`.
 */
export function parseProgramGenerationPayload(payload: string): ProgramNormalizationRequest {
  try {
    return toNormalizationRequest(parseGenerationContext(payload));
  } catch (error) {
    const message = error instanceof Error ? error.message : "Invalid payload.";
    throw new HttpsError("invalid-argument", message);
  }
}

/**
 * Returns a cached program when a prior invocation completed but the client never received the
 * response (e.g. DEADLINE_EXCEEDED after the server finished). Enables idempotent retries without
 * burning another LLM call.
 */
export function cachedProgramFromMarker(snapshot: GenerationMarkerSnapshot): unknown | null {
  if (!snapshot.exists || snapshot.status !== "completed") {
    return null;
  }
  const program = snapshot.program;
  if (program != null && typeof program === "object") {
    return program;
  }
  return null;
}

/** True when a stuck `pending` marker can be reclaimed (client timed out / function crashed). */
export function isStalePendingMarker(
  snapshot: GenerationMarkerSnapshot,
  nowMs: number = Date.now(),
): boolean {
  if (!snapshot.exists || snapshot.status !== "pending") {
    return false;
  }
  if (snapshot.startedAtMs == null) {
    return true;
  }
  return nowMs - snapshot.startedAtMs > GENERATION_CALLABLE_TIMEOUT_MS;
}

/**
 * Guards the Firestore generation slot before claiming `pending`. Rejects concurrent in-flight
 * duplicates (`aborted`). Completed markers (with or without a cached program) are reclaimable so
 * the user can regenerate after editing onboarding. Stale `pending` markers are reclaimable after
 * a client timeout.
 */
export function assertCanClaimGenerationSlot(
  snapshot: GenerationMarkerSnapshot,
  nowMs: number = Date.now(),
): void {
  if (!snapshot.exists) return;
  if (snapshot.status === "completed") {
    return;
  }
  if (snapshot.status === "pending") {
    if (isStalePendingMarker(snapshot, nowMs)) {
      return;
    }
    throw new HttpsError("aborted", "Program generation already in progress.");
  }
}

function generationMarkerRef(uid: string): FirebaseFirestore.DocumentReference {
  return admin.firestore().doc(`users/${uid}/programs_generation/latest`);
}

function snapshotFromDoc(
  doc: FirebaseFirestore.DocumentSnapshot,
): GenerationMarkerSnapshot {
  const data = doc.data();
  const startedAt = data?.startedAt as FirebaseFirestore.Timestamp | undefined;
  return {
    exists: doc.exists,
    status: data?.status as GenerationMarkerStatus | undefined,
    program: data?.program,
    startedAtMs: startedAt?.toMillis() ?? null,
  };
}

async function loadCachedProgram(uid: string): Promise<unknown | null> {
  const doc = await generationMarkerRef(uid).get();
  return cachedProgramFromMarker(snapshotFromDoc(doc));
}

async function claimGenerationSlot(uid: string): Promise<void> {
  const generationRef = generationMarkerRef(uid);
  try {
    await admin.firestore().runTransaction(async (txn) => {
      const doc = await txn.get(generationRef);
      const snapshot = snapshotFromDoc(doc);
      assertCanClaimGenerationSlot(snapshot);
      if (snapshot.exists) {
        txn.delete(generationRef);
      }
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

async function markGenerationCompleted(uid: string, program: unknown): Promise<void> {
  const generationRef = generationMarkerRef(uid);
  await admin.firestore().runTransaction(async (txn) => {
    const doc = await txn.get(generationRef);
    if (!doc.exists || doc.data()?.status !== "pending") {
      throw new HttpsError("internal", "Generation state lost.");
    }
    txn.set(generationRef, {
      status: "completed",
      generatedAt: admin.firestore.FieldValue.serverTimestamp(),
      program,
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
