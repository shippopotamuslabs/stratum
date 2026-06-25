import { collectImportViolations, ImportValidationError } from "./importValidation";
import {
  buildImportAgentSystemPrompt,
  EXECUTE_ON_FULL_DESCRIPTION,
  EXECUTE_ON_SAMPLE_DESCRIPTION,
  WRITE_CONVERSION_SCRIPT_DESCRIPTION,
} from "./importDomainRules";

export type ImportJobStatus = "pending_agent" | "agent_running" | "ready" | "failed";

export type ImportProgressPhase =
  | "reading_data"
  | "creating_converter"
  | "validating_sample"
  | "converting_full"
  | "failed";

export interface LatestConversion {
  storagePath: string;
  rowCount: number;
  sessionCount: number;
  sizeBytes: number;
  createdAt: FirebaseFirestore.Timestamp;
}

export interface ImportJobDoc {
  status: ImportJobStatus;
  rawStoragePath: string;
  sampleStoragePath: string;
  fullSliceStoragePath?: string;
  scriptStoragePath?: string;
  latestConversion?: LatestConversion;
  errorMessage?: string;
  agentTurnCount?: number;
  progressPhase?: ImportProgressPhase;
  progressMessage?: string;
  progressUpdatedAt?: FirebaseFirestore.Timestamp;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface NormalizedSet {
  id: string;
  sessionId: string;
  exerciseNameRaw: string;
  liftId?: string;
  setIndex: number;
  weightAmount: number;
  weightUnit: string;
  reps: number;
  rir?: number;
  completedAt: string;
}

export interface NormalizedSession {
  id: string;
  startedAt: string;
  endedAt?: string;
  sourceApp?: string;
}

export interface NormalizedHistory {
  sessions: NormalizedSession[];
  sets: NormalizedSet[];
}

export function normalizeWeightUnit(raw: string): "Lb" | "Kg" {
  const trimmed = raw.trim();
  const normalized = trimmed.toLowerCase().replace(/\.$/, "").replace(/s$/, "");
  if (normalized === "lb" || normalized === "pound" || normalized === "pounds") {
    return "Lb";
  }
  if (normalized === "kg" || normalized === "kilogram" || normalized === "kilograms" || normalized === "kilo") {
    return "Kg";
  }
  if (trimmed === "Lb" || trimmed === "Kg") {
    return trimmed;
  }
  throw new Error(`Unsupported weight unit: ${raw}`);
}

export function normalizeImportTimestamp(raw: string): string {
  const trimmed = raw.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return `${trimmed}T00:00:00Z`;
  }
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(trimmed)) {
    return trimmed.endsWith("Z") ? trimmed : `${trimmed}Z`;
  }
  const parsed = Date.parse(trimmed);
  if (Number.isNaN(parsed)) {
    throw new Error(`Invalid timestamp: ${raw}`);
  }
  if (trimmed.includes("T") && (trimmed.endsWith("Z") || /[+-]\d{2}:\d{2}$/.test(trimmed))) {
    return trimmed;
  }
  return new Date(parsed).toISOString();
}

export function validateNormalizedHistory(data: unknown): NormalizedHistory {
  if (!data || typeof data !== "object") {
    throw new Error("Output must be a JSON object.");
  }
  const obj = data as Record<string, unknown>;
  if (!Array.isArray(obj.sessions) || !Array.isArray(obj.sets)) {
    throw new Error("Output must contain sessions and sets arrays.");
  }
  for (const session of obj.sessions) {
    if (!session || typeof session !== "object") {
      throw new Error("Invalid session entry.");
    }
    const s = session as Record<string, unknown>;
    if (typeof s.id !== "string" || typeof s.startedAt !== "string") {
      throw new Error("Each session requires id and startedAt.");
    }
    s.startedAt = normalizeImportTimestamp(s.startedAt);
    if (typeof s.endedAt === "string") {
      s.endedAt = normalizeImportTimestamp(s.endedAt);
    }
  }
  for (const set of obj.sets) {
    if (!set || typeof set !== "object") {
      throw new Error("Invalid set entry.");
    }
    const row = set as Record<string, unknown>;
    if (
      typeof row.id !== "string" ||
      typeof row.sessionId !== "string" ||
      typeof row.exerciseNameRaw !== "string" ||
      typeof row.setIndex !== "number" ||
      typeof row.weightAmount !== "number" ||
      typeof row.weightUnit !== "string" ||
      typeof row.reps !== "number" ||
      typeof row.completedAt !== "string"
    ) {
      throw new Error("Each set requires id, sessionId, exerciseNameRaw, setIndex, weightAmount, weightUnit, reps, completedAt.");
    }
    row.weightUnit = normalizeWeightUnit(row.weightUnit as string);
    row.completedAt = normalizeImportTimestamp(row.completedAt as string);
  }
  const history = obj as unknown as NormalizedHistory;
  const violations = collectImportViolations(history);
  if (violations.length > 0) {
    throw new ImportValidationError(violations);
  }
  return history;
}

export const IMPORT_AGENT_SYSTEM_PROMPT = buildImportAgentSystemPrompt();

export const TOOL_DEFINITIONS = [
  {
    type: "function" as const,
    function: {
      name: "write_conversion_script",
      description: WRITE_CONVERSION_SCRIPT_DESCRIPTION,
      parameters: {
        type: "object",
        properties: {
          intent: {
            type: "string",
            description: "Short user-facing description of what you are doing (max 120 chars)",
          },
          script: {
            type: "string",
            description:
              "Python source defining convert(raw_input: str) -> dict with sessions[] and sets[] obeying Stratum domain rules (reps>0, etc.)",
          },
        },
        required: ["intent", "script"],
      },
    },
  },
  {
    type: "function" as const,
    function: {
      name: "execute_on_sample",
      description: EXECUTE_ON_SAMPLE_DESCRIPTION,
      parameters: {
        type: "object",
        properties: {
          intent: { type: "string" },
          scriptStoragePath: { type: "string" },
        },
        required: ["intent", "scriptStoragePath"],
      },
    },
  },
  {
    type: "function" as const,
    function: {
      name: "execute_on_full",
      description: EXECUTE_ON_FULL_DESCRIPTION,
      parameters: {
        type: "object",
        properties: {
          intent: { type: "string" },
          scriptStoragePath: { type: "string" },
        },
        required: ["intent", "scriptStoragePath"],
      },
    },
  },
];

export const DEFAULT_INTENT: Record<string, string> = {
  write_conversion_script: "Building a converter for your file format",
  execute_on_sample: "Running a quick test on a sample of your data",
  execute_on_full: "Converting your full training history",
};

export const PHASE_BY_TOOL: Record<string, ImportProgressPhase> = {
  write_conversion_script: "creating_converter",
  execute_on_sample: "validating_sample",
  execute_on_full: "converting_full",
};

const PROGRESS_MESSAGE_BY_PHASE: Partial<Record<ImportProgressPhase, string>> = {
  reading_data: "Reading your data…",
  creating_converter: DEFAULT_INTENT.write_conversion_script,
  validating_sample: DEFAULT_INTENT.execute_on_sample,
  converting_full: DEFAULT_INTENT.execute_on_full,
};

export function looksLikeTechnicalProgressLeak(message: string): boolean {
  const patterns = [
    /traceback/i,
    /\berror:/i,
    /^file "/i,
    /line \d+/i,
    /requires id,/i,
    /sessionId,/i,
    /exerciseNameRaw/i,
    /KeyError/i,
    /AttributeError/i,
    /TypeError/i,
    /ValueError/i,
    /def convert/i,
    /import json/i,
    /\.py\b/i,
    /must be a JSON object/i,
    /sessions and sets arrays/i,
  ];
  return patterns.some((pattern) => pattern.test(message));
}

export function sanitizeClientProgressMessage(
  message: string | undefined,
  phase: ImportProgressPhase | undefined,
): string | null {
  if (!message) {
    return null;
  }
  if (!looksLikeTechnicalProgressLeak(message)) {
    return message;
  }
  if (phase && PROGRESS_MESSAGE_BY_PHASE[phase]) {
    return PROGRESS_MESSAGE_BY_PHASE[phase] ?? null;
  }
  return null;
}

export function sanitizeIntent(intent: unknown, toolName: string): string {
  const fallback = DEFAULT_INTENT[toolName] ?? "Working on your import";
  if (typeof intent !== "string") {
    return fallback;
  }
  const cleaned = intent.replace(/\s+/g, " ").trim().slice(0, 120);
  if (cleaned.length === 0 || looksLikeTechnicalProgressLeak(cleaned)) {
    return fallback;
  }
  return cleaned;
}
