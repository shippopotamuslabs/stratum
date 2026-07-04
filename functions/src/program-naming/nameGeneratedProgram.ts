/**
 * Names a generated program and its workouts via OpenRouter SMALL (flash).
 */
import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  programGenerationSessionId,
} from "../openrouterSession";
import {
  buildProgramNamingInput,
  PROGRAM_NAMING_SYSTEM_PROMPT,
  parseNameProgramResponse,
  programNamingOutputSchema,
  type NameProgramRequestPayload,
} from "./programNamingPrompt";

export const nameGeneratedProgram = onCall(
  {
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL_SMALL"],
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }

    const data = request.data as { namingPayload?: NameProgramRequestPayload };
    const namingPayload = data.namingPayload;
    if (!namingPayload || typeof namingPayload !== "object") {
      throw new HttpsError("invalid-argument", "namingPayload is required.");
    }
    if (!Array.isArray(namingPayload.program?.workouts) || namingPayload.program.workouts.length === 0) {
      throw new HttpsError("invalid-argument", "program.workouts must be a non-empty array.");
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL_SMALL ?? "deepseek/deepseek-v4-flash";
    if (!apiKey) {
      throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
    }

    const sessionId = programGenerationSessionId(request.auth.uid);
    const client = createOpenRouterClient(apiKey, sessionId);
    const extras = buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true });

    const response = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: PROGRAM_NAMING_SYSTEM_PROMPT },
        { role: "user", content: JSON.stringify(buildProgramNamingInput(namingPayload)) },
      ],
      response_format: {
        type: "json_schema",
        json_schema: programNamingOutputSchema(),
      },
      temperature: 0.3,
      ...extras,
    } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);

    const content = response.choices[0]?.message?.content;
    if (!content) {
      return { programName: null, workouts: [] };
    }

    const parsed = parseNameProgramResponse(content);
    if (!parsed) {
      return { programName: null, workouts: [] };
    }

    return {
      programName: parsed.programName,
      workouts: parsed.workouts,
    };
  },
);
