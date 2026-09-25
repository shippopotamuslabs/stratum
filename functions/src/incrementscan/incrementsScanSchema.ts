/**
 * Schema + parser for the `inferEquipmentIncrements` Firebase Callable (PR-3).
 *
 * Maps a photo of a selectorized (pin-stack) weight label to a structured tier
 * list in a single tagged unit, plus optional add-on/adder stops. The client
 * converts rows to storage lb and gates prefills with
 * `ValidateIncrementTiersUseCase` — the server only sanitizes.
 *
 * Micro stops are bonus-only: malformed micro is dropped, never blocks tiers.
 * This module is pure (no network, no secrets) and stays inert until PR-3
 * wires the callable — it is deliberately NOT exported from `index.ts` yet.
 */

export const SCAN_WEIGHT_UNITS = ["lb", "kg"] as const;

export type ScanWeightUnit = (typeof SCAN_WEIGHT_UNITS)[number];

/** One weight band: [lower inclusive, upper exclusive); null upper = open-ended. */
export interface ScannedTierRow {
  lower: number;
  upper: number | null;
  step: number;
}

export interface ScannedTiers {
  unit: ScanWeightUnit;
  tiers: ScannedTierRow[];
  microStops: number[];
}

/** A pin-stack label rarely prints more than a handful of bands; cap the wire shape. */
export const INCREMENT_SCAN_MAX_TIERS = 8;
/** Adder/drop-set pins are a short list; cap the wire shape. */
export const INCREMENT_SCAN_MAX_MICRO_STOPS = 6;

const SCAN_WEIGHT_UNIT_SET = new Set<string>(SCAN_WEIGHT_UNITS);

export const INCREMENT_SCAN_SYSTEM_PROMPT = `You read a selectorized weight-stack (pin-stack) label photo for a workout app.

Return ONLY what is printed on the label:
- tiers: the labeled weight bands as {lower, upper, step} in ONE unit ("lb" or "kg", matching the label).
  The top band is open-ended: use upper null for it.
- microStops: add-on / adder / plate-mate stop weights ONLY when such a pin or add-on weight
  is legible on the label; otherwise an empty array.

Rules:
- Never invent bands, steps, or stops that are not printed on the label.
- Numbers only — no unit strings, no ranges, no prose inside values.
- If the label is illegible or no bands can be read, return empty tiers (never guess).`;

export const INCREMENT_SCAN_USER_PROMPT = `Transcribe the weight bands and any add-on stops printed on this stack label.`;

export function incrementScanJsonSchema() {
  return {
    name: "equipment_increments_scan",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        unit: { type: "string", enum: [...SCAN_WEIGHT_UNITS] },
        tiers: {
          type: "array",
          maxItems: INCREMENT_SCAN_MAX_TIERS,
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              lower: { type: "number" },
              upper: { type: ["number", "null"] },
              step: { type: "number" },
            },
            required: ["lower", "upper", "step"],
          },
        },
        microStops: {
          type: "array",
          maxItems: INCREMENT_SCAN_MAX_MICRO_STOPS,
          items: { type: "number" },
        },
      },
      required: ["unit", "tiers", "microStops"],
    },
  };
}

/**
 * Parse + sanitize a model payload. Unknown top-level or per-row fields are
 * dropped (never echoed). Rows are sorted by lower bound; gaps and overlaps
 * are tolerated here — strict contiguity is enforced client-side by
 * `ValidateIncrementTiersUseCase`. Empty or fully-invalid tiers yield `[]`;
 * the unit falls back to "lb" (the storage unit, so no conversion applies).
 */
export function parseScannedTiers(payload: unknown): ScannedTiers {
  if (!payload || typeof payload !== "object") {
    return emptyScan();
  }
  const body = payload as Record<string, unknown>;
  const unit = resolveUnit(body.unit);
  const tiers = resolveTierRows(body.tiers);
  const microStops = resolveMicroStops(body.microStops);
  return { unit, tiers, microStops };
}

function emptyScan(): ScannedTiers {
  return { unit: "lb", tiers: [], microStops: [] };
}

function resolveUnit(raw: unknown): ScanWeightUnit {
  if (typeof raw === "string" && SCAN_WEIGHT_UNIT_SET.has(raw)) {
    return raw as ScanWeightUnit;
  }
  return "lb";
}

function resolveTierRows(raw: unknown): ScannedTierRow[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const rows: ScannedTierRow[] = [];
  for (const entry of raw) {
    const row = resolveTierRow(entry);
    if (row) {
      rows.push(row);
    }
  }
  rows.sort((a, b) => a.lower - b.lower);
  return rows.slice(0, INCREMENT_SCAN_MAX_TIERS);
}

function resolveTierRow(entry: unknown): ScannedTierRow | null {
  if (!entry || typeof entry !== "object") {
    return null;
  }
  const row = entry as Record<string, unknown>;
  const lower = row.lower;
  const step = row.step;
  if (
    typeof lower !== "number" ||
    !Number.isFinite(lower) ||
    lower < 0 ||
    typeof step !== "number" ||
    !Number.isFinite(step) ||
    step <= 0
  ) {
    return null;
  }
  const upper = resolveUpper(row.upper, lower);
  if (upper === undefined) {
    return null;
  }
  return { lower, upper, step };
}

/** Returns undefined for an invalid upper so the whole row is dropped. */
function resolveUpper(raw: unknown, lower: number): number | null | undefined {
  if (raw === null) {
    return null;
  }
  if (raw === undefined) {
    return undefined;
  }
  if (typeof raw !== "number" || !Number.isFinite(raw) || raw <= lower) {
    return undefined;
  }
  return raw;
}

function resolveMicroStops(raw: unknown): number[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const seen = new Set<number>();
  for (const entry of raw) {
    if (typeof entry === "number" && Number.isFinite(entry) && entry > 0) {
      seen.add(entry);
    }
  }
  return [...seen].sort((a, b) => a - b).slice(0, INCREMENT_SCAN_MAX_MICRO_STOPS);
}
