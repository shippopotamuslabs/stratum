import OpenAI from "openai";
import {
  buildOpenRouterProviderRouting,
  type OpenRouterRoutingOptions,
} from "./openrouterRouting";

export const OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
export const OPENROUTER_SESSION_ID_MAX = 256;

/** Stable sticky-routing key for a multi-turn import agent job. */
export function importAgentSessionId(uid: string, jobId: string): string {
  return sanitizeOpenRouterSessionId(`stratum-import:${uid}:${jobId}`);
}

/** Sticky-routing key for custom-lift enrichment during import. */
export function customLiftInferenceSessionId(uid: string): string {
  return sanitizeOpenRouterSessionId(`stratum-custom-lifts:${uid}`);
}

/** Sticky-routing key for transient equipment inference during import. */
export function importEquipmentInferenceSessionId(uid: string): string {
  return sanitizeOpenRouterSessionId(`stratum-import-equipment:${uid}`);
}

export function sanitizeOpenRouterSessionId(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) {
    throw new Error("OpenRouter session_id must be non-empty.");
  }
  return trimmed.length <= OPENROUTER_SESSION_ID_MAX
    ? trimmed
    : trimmed.slice(0, OPENROUTER_SESSION_ID_MAX);
}

export function createOpenRouterClient(apiKey: string, sessionId: string): OpenAI {
  const stickySessionId = sanitizeOpenRouterSessionId(sessionId);
  return new OpenAI({
    apiKey,
    baseURL: OPENROUTER_BASE_URL,
    defaultHeaders: {
      "X-Session-Id": stickySessionId,
    },
  });
}

export function buildOpenRouterChatCompletionExtras(
  sessionId: string,
  routingOptions: OpenRouterRoutingOptions = {},
): {
  session_id: string;
  provider: ReturnType<typeof buildOpenRouterProviderRouting>;
} {
  return {
    session_id: sanitizeOpenRouterSessionId(sessionId),
    provider: buildOpenRouterProviderRouting({
      stickySession: true,
      ...routingOptions,
    }),
  };
}
