import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  sanitizeProgramGenerationProgressMessage,
  type ProgramGenerationProgressPhase,
} from "./programGenerationProgress";

type GenerationMarkerStatus = "pending" | "completed";

/**
 * Pollable progress for the blocking `generateProgram` callable. Mirrors `getImportJobStatus` so the
 * Android client can show model-reported status while the generation request is in flight.
 */
export const getProgramGenerationStatus = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const uid = request.auth.uid;
    const snap = await admin.firestore().doc(`users/${uid}/programs_generation/latest`).get();
    if (!snap.exists) {
      return {
        status: null,
        progressPhase: null,
        progressMessage: null,
      };
    }
    const data = snap.data()!;
    const status = data.status as GenerationMarkerStatus | undefined;
    const progressPhase = data.progressPhase as ProgramGenerationProgressPhase | undefined;
    return {
      status: status ?? null,
      progressPhase: progressPhase ?? null,
      progressMessage: sanitizeProgramGenerationProgressMessage(
        data.progressMessage as string | undefined,
        progressPhase,
      ),
    };
  },
);
