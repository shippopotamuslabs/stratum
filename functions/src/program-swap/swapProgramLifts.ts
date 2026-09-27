/**
 * Optional OpenRouter lift-swap pass — the only LLM step in program generation.
 */
import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  programGenerationSessionId,
} from "../openrouterSession";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import {
  buildLiftSwapInput,
  LIFT_SWAP_SYSTEM_PROMPT,
  liftSwapOutputSchema,
  type LiftSwapReviewPayload,
} from "./liftSwapPrompt";

interface SwapRequestRow {
  workoutPosition: number;
  liftPosition: number;
  newLiftId: string;
}

function parseSwapResponse(content: string): SwapRequestRow[] {
  const parsed = JSON.parse(content) as { swaps?: unknown };
  if (!Array.isArray(parsed.swaps)) return [];
  const out: SwapRequestRow[] = [];
  for (const row of parsed.swaps) {
    if (!row || typeof row !== "object") continue;
    const r = row as Record<string, unknown>;
    if (
      typeof r.workoutPosition === "number" &&
      typeof r.liftPosition === "number" &&
      typeof r.newLiftId === "string"
    ) {
      out.push({
        workoutPosition: r.workoutPosition,
        liftPosition: r.liftPosition,
        newLiftId: r.newLiftId,
      });
    }
  }
  return out;
}

export const swapProgramLifts = onCall(
  {
    enforceAppCheck: true,
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"],
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    await assertUserEntitled(request.auth.uid);

    const data = request.data as { reviewPayload?: LiftSwapReviewPayload };
    const reviewPayload = data.reviewPayload;
    if (!reviewPayload || typeof reviewPayload !== "object") {
      throw new HttpsError("invalid-argument", "reviewPayload is required.");
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
    if (!apiKey) {
      throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
    }

    const sessionId = programGenerationSessionId(request.auth.uid);
    const client = createOpenRouterClient(apiKey, sessionId);
    const swapInput = buildLiftSwapInput(reviewPayload);
    const extras = buildOpenRouterChatCompletionExtras(sessionId);

    const response = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: LIFT_SWAP_SYSTEM_PROMPT },
        { role: "user", content: JSON.stringify(swapInput) },
      ],
      response_format: { type: "json_schema", json_schema: liftSwapOutputSchema() },
      temperature: 0.2,
      ...extras,
    } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);

    const content = response.choices[0]?.message?.content;
    if (!content) {
      return { swaps: [] };
    }

    try {
      return { swaps: parseSwapResponse(content) };
    } catch {
      return { swaps: [] };
    }
  },
);
