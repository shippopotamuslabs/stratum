import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  importEquipmentInferenceSessionId,
} from "../openrouterSession";
import {
  buildImportEquipmentUserPrompt,
  IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH,
  IMPORT_EQUIPMENT_SYSTEM_PROMPT,
  importEquipmentJsonSchema,
  parseInferredEquipment,
  type InferImportEquipmentExerciseInput,
} from "./inferImportEquipmentSchema";
import { assertUserEntitled } from "../entitlement/assertEntitled";

/**
 * Transiently maps a user's distinct imported exercise names to `GymEquipment` enum values via
 * `OPENROUTER_MODEL_SMALL` (Flash). The returned name -> equipment map lives only for the
 * duration of `ResolveImportLiftsUseCase` (equipment closure + sparse-import fallback) and is
 * never persisted. Mirrors `inferCustomLifts` for auth/secrets/routing.
 */
export const inferImportEquipment = onCall(
  {
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL_SMALL"],
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    await assertUserEntitled(request.auth.uid);

    const data = request.data as { exercises?: InferImportEquipmentExerciseInput[] };
    const exercises = data.exercises;
    if (!Array.isArray(exercises) || exercises.length === 0) {
      throw new HttpsError("invalid-argument", "exercises must be a non-empty array.");
    }
    if (exercises.length > IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH) {
      throw new HttpsError(
        "invalid-argument",
        `At most ${IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH} exercises per request.`,
      );
    }

    const normalized: InferImportEquipmentExerciseInput[] = [];
    const seen = new Set<string>();
    for (const entry of exercises) {
      const name = typeof entry?.name === "string" ? entry.name.trim() : "";
      const loggedSetCount =
        typeof entry?.loggedSetCount === "number" && entry.loggedSetCount > 0
          ? Math.floor(entry.loggedSetCount)
          : 1;
      if (!name || seen.has(name)) {
        continue;
      }
      seen.add(name);
      normalized.push({ name, loggedSetCount });
    }
    if (normalized.length === 0) {
      throw new HttpsError("invalid-argument", "No valid exercise names provided.");
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL_SMALL ?? "deepseek/deepseek-v4-flash";
    if (!apiKey) {
      throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
    }

    const sessionId = importEquipmentInferenceSessionId(request.auth.uid);
    const client = createOpenRouterClient(apiKey, sessionId);
    const completion = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: IMPORT_EQUIPMENT_SYSTEM_PROMPT },
        { role: "user", content: buildImportEquipmentUserPrompt(normalized) },
      ],
      response_format: {
        type: "json_schema",
        json_schema: importEquipmentJsonSchema(),
      },
      ...buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }),
    } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);

    const content = completion.choices[0]?.message?.content;
    if (!content) {
      throw new HttpsError("internal", "Empty model response.");
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(content);
    } catch {
      throw new HttpsError("internal", "Model returned invalid JSON.");
    }

    const requestedNames = new Set(normalized.map((entry) => entry.name));
    const lifts = parseInferredEquipment(parsed, requestedNames);
    return { lifts };
  },
);
