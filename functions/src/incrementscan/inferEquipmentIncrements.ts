import OpenAI from "openai";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  sanitizeOpenRouterSessionId,
} from "../openrouterSession";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { createLogger } from "../observability/structuredLog";
import {
  buildResultMeta,
  logCallableVersion,
  type CallableResultMeta,
} from "../versioning/callableVersioning";
import {
  INCREMENT_SCAN_SYSTEM_PROMPT,
  INCREMENT_SCAN_USER_PROMPT,
  incrementScanJsonSchema,
  parseScannedTiers,
  type ScannedTiers,
} from "./incrementsScanSchema";
import { admitIncrementScan } from "./scanQuota";

/**
 * Photo of a selectorized (pin-stack) weight label in, structured tiers out.
 *
 * Transient by design: the photo is sent inline as base64 in the callable
 * payload and is never persisted server-side. Auth + membership + daily /
 * monthly quota gate every call; failures degrade client-side to manual entry.
 * App Check is enforced (mirrors the sibling LLM callables); dev builds must
 * register a debug token or calls will be rejected.
 *
 * Versioning reference (STR-48): this callable is implicit v1 and spreads an
 * `_meta` block into its result. Clients ignore unknown top-level fields, so
 * old app versions parse the versioned result identically.
 */

const log = createLogger({ service: "inferEquipmentIncrements" });

/**
 * Server-side hard cap on the decoded image payload. The client downscales
 * before upload; this is defense in depth (mirrors `programTemplateLimits`).
 */
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

export const SCAN_IMAGE_MIME_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

export type ScanImageMimeType = (typeof SCAN_IMAGE_MIME_TYPES)[number];

const SCAN_IMAGE_MIME_SET: ReadonlySet<string> = new Set(SCAN_IMAGE_MIME_TYPES);

const BASE64_CHARS = /^[A-Za-z0-9+/=]+$/;

export type ScanVisionConfig = {
  apiKey: string;
  model: string;
};

/**
 * Validates the `{ imageBase64, mimeType }` payload and folds it into the
 * `data:<mime>;base64,…` URL the OpenAI SDK vision part expects.
 * Throws `invalid-argument` on wrong mime, malformed base64, empty payloads,
 * or decoded images over `MAX_IMAGE_BYTES`.
 */
export function resolveScanImageDataUri(data: unknown): string {
  const body = (data ?? {}) as Record<string, unknown>;
  const mimeType = body.mimeType;
  if (typeof mimeType !== "string" || !SCAN_IMAGE_MIME_SET.has(mimeType)) {
    throw new HttpsError(
      "invalid-argument",
      "mimeType must be one of image/jpeg, image/png, image/webp.",
    );
  }
  const imageBase64 = body.imageBase64;
  if (typeof imageBase64 !== "string" || imageBase64.length === 0) {
    throw new HttpsError("invalid-argument", "imageBase64 must be a non-empty base64 string.");
  }
  const compact = imageBase64.replace(/\s+/g, "");
  if (compact.length === 0 || !BASE64_CHARS.test(compact)) {
    throw new HttpsError("invalid-argument", "imageBase64 is not valid base64.");
  }
  const bytes = Buffer.from(compact, "base64");
  if (bytes.length === 0) {
    throw new HttpsError("invalid-argument", "imageBase64 decoded to an empty image.");
  }
  if (bytes.length > MAX_IMAGE_BYTES) {
    throw new HttpsError(
      "invalid-argument",
      `Image is too large (${bytes.length} bytes). Maximum is ${MAX_IMAGE_BYTES} bytes.`,
    );
  }
  return `data:${mimeType};base64,${compact}`;
}

/**
 * Fail-closed secret resolution: both the API key and the vision model slug
 * must be present. There is deliberately no model fallback — defaulting to a
 * non-vision slug would silently misroute the request.
 */
export function resolveScanVisionConfig(env: NodeJS.ProcessEnv = process.env): ScanVisionConfig {
  const apiKey = env.OPENROUTER_API_KEY;
  if (!apiKey) {
    throw new HttpsError("failed-precondition", "OPENROUTER_API_KEY missing.");
  }
  const model = env.OPENROUTER_VISION_MODEL;
  if (!model) {
    throw new HttpsError("failed-precondition", "OPENROUTER_VISION_MODEL missing.");
  }
  return { apiKey, model };
}

/**
 * Maps the model completion text to sanitized tiers. Empty content and
 * non-JSON content are `internal` (provider fault); schema-shape problems are
 * sanitized by `parseScannedTiers` (empty tiers = "couldn't read it" path).
 */
export function parseScanModelContent(content: string | null | undefined): ScannedTiers {
  if (!content) {
    throw new HttpsError("internal", "Empty model response.");
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    throw new HttpsError("internal", "Model returned invalid JSON.");
  }
  return parseScannedTiers(parsed);
}

export type VersionedScanResult = ScannedTiers & { _meta: CallableResultMeta };

/**
 * Attach the `_meta` version baseline to a sanitized scan. Pure — the
 * callable handler is the only caller; unit tests assert the wire shape here
 * because the handler's success path needs live quota + model calls.
 */
export function buildScanResult(scan: ScannedTiers): VersionedScanResult {
  return { ...scan, _meta: buildResultMeta(1) };
}

export const inferEquipmentIncrements = onCall(
  {
    enforceAppCheck: true,
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_VISION_MODEL"],
    // Single vision call with structured output; keep headroom like the siblings.
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const uid = request.auth.uid;
    await assertUserEntitled(uid);

    const dataUri = resolveScanImageDataUri(request.data);
    const { apiKey, model } = resolveScanVisionConfig();
    await admitIncrementScan(uid);

    const sessionId = sanitizeOpenRouterSessionId(`stratum-increment-scan:${uid}`);
    const client = createOpenRouterClient(apiKey, sessionId);
    let content: string | null | undefined;
    try {
      const completion = await client.chat.completions.create({
        model,
        messages: [
          { role: "system", content: INCREMENT_SCAN_SYSTEM_PROMPT },
          {
            role: "user",
            content: [
              { type: "image_url", image_url: { url: dataUri } },
              { type: "text", text: INCREMENT_SCAN_USER_PROMPT },
            ],
          },
        ],
        response_format: {
          type: "json_schema",
          json_schema: incrementScanJsonSchema(),
        },
        ...buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }),
      } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
      content = completion.choices[0]?.message?.content;
    } catch (err) {
      if (err instanceof HttpsError) {
        throw err;
      }
      console.error(`inferEquipmentIncrements model call failed for ${uid}:`, err);
      throw new HttpsError("internal", "Scan request failed. Please try again.");
    }

    const scan = parseScanModelContent(content);
    logCallableVersion(log, "inferEquipmentIncrements", 1, { uid });
    return buildScanResult(scan);
  },
);
