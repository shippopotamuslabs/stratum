import type OpenAI from "openai";
import type { SandboxResult } from "./pythonSandbox";

export const VALID_NORMALIZED = {
  sessions: [{ id: "session-1", startedAt: "2025-01-01T10:00:00Z" }],
  sets: [
    {
      id: "set-1",
      sessionId: "session-1",
      exerciseNameRaw: "Squat",
      setIndex: 1,
      weightAmount: 100,
      weightUnit: "Lb",
      reps: 8,
      completedAt: "2025-01-01T10:05:00Z",
    },
  ],
};

export const VALID_NORMALIZED_JSON = JSON.stringify(VALID_NORMALIZED);

export const SAMPLE_CONVERTER_SCRIPT = `
def convert(raw_input: str) -> dict:
    return {
        "sessions": [{"id": "session-1", "startedAt": "2025-01-01T10:00:00Z"}],
        "sets": [{
            "id": "set-1",
            "sessionId": "session-1",
            "exerciseNameRaw": "Squat",
            "setIndex": 1,
            "weightAmount": 100,
            "weightUnit": "Lb",
            "reps": 8,
            "completedAt": "2025-01-01T10:05:00Z",
        }],
    }
`;

export const INVALID_CONVERTER_SCRIPT = `
def convert(raw_input: str) -> dict:
    return {"sessions": [], "sets": [{"bad": True}]}
`;

export function makeToolCall(
  id: string,
  name: string,
  args: Record<string, unknown>,
): OpenAI.Chat.Completions.ChatCompletionMessageToolCall {
  return {
    id,
    type: "function",
    function: {
      name,
      arguments: JSON.stringify(args),
    },
  };
}

export function makeCompletion(
  toolCalls: OpenAI.Chat.Completions.ChatCompletionMessageToolCall[],
  content: string | null = null,
): OpenAI.Chat.Completions.ChatCompletion {
  return {
    id: "completion-1",
    object: "chat.completion",
    created: Date.now(),
    model: "test-model",
    choices: [
      {
        index: 0,
        message: {
          role: "assistant",
          content,
          refusal: null,
          tool_calls: toolCalls,
        },
        finish_reason: toolCalls.length > 0 ? "tool_calls" : "stop",
        logprobs: null,
      },
    ],
  };
}

export function sandboxSuccess(overrides: Partial<SandboxResult> = {}): SandboxResult {
  const outputJson = overrides.outputJson ?? VALID_NORMALIZED_JSON;
  const parsed = JSON.parse(outputJson) as { sets?: unknown[]; sessions?: unknown[] };
  return {
    outputJson,
    rowCount: overrides.rowCount ?? (parsed.sets?.length ?? 0),
    sessionCount: overrides.sessionCount ?? (parsed.sessions?.length ?? 0),
  };
}

export function baseJobData() {
  return {
    status: "pending_agent",
    sampleStoragePath: "imports/user-1/job-1/sample/export.csv",
    rawStoragePath: "imports/user-1/job-1/raw/export.csv",
  };
}
