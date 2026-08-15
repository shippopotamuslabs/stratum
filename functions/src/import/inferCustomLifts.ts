import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  customLiftInferenceSessionId,
} from "../openrouterSession";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import {
  buildCustomLiftUserPrompt,
  CUSTOM_LIFT_INFERENCE_MAX_BATCH,
  CUSTOM_LIFT_SYSTEM_PROMPT,
  customLiftInferenceJsonSchema,
  parseInferredCustomLifts,
  type InferCustomLiftsExerciseInput,
} from "./customLiftInferenceSchema";

export const inferCustomLifts = onCall(
  {
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL_SMALL"],
    // Default Cloud Run timeout is 60s; keep headroom for large structured JSON batches.
    timeoutSeconds: 300,
    memory: "512MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    await assertUserEntitled(request.auth.uid);

    const data = request.data as { exercises?: InferCustomLiftsExerciseInput[] };
    const exercises = data.exercises;
    if (!Array.isArray(exercises) || exercises.length === 0) {
      throw new HttpsError("invalid-argument", "exercises must be a non-empty array.");
    }
    if (exercises.length > CUSTOM_LIFT_INFERENCE_MAX_BATCH) {
      throw new HttpsError(
        "invalid-argument",
        `At most ${CUSTOM_LIFT_INFERENCE_MAX_BATCH} exercises per request.`,
      );
    }

    const normalized: InferCustomLiftsExerciseInput[] = [];
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

    const sessionId = customLiftInferenceSessionId(request.auth.uid);
    const client = createOpenRouterClient(apiKey, sessionId);
    const completion = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: CUSTOM_LIFT_SYSTEM_PROMPT },
        { role: "user", content: buildCustomLiftUserPrompt(normalized) },
      ],
      response_format: {
        type: "json_schema",
        json_schema: customLiftInferenceJsonSchema(),
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
    const lifts = parseInferredCustomLifts(parsed, requestedNames);
    return { lifts };
  },
);
