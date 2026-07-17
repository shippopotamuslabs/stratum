/**
 * Single-shot (optional one repair) OpenRouter conversion of a spreadsheet view → NormalizedProgramTemplate.
 */
import OpenAI from "openai";
import type {
  NormalizedProgramTemplate,
  NormalizedProgramTemplateSet,
} from "../deterministic/types";
import { validateProgramTemplate } from "../deterministic/types";
import {
  buildProgramTemplateRepairPrompt,
  buildProgramTemplateUserPrompt,
  PROGRAM_TEMPLATE_SYSTEM_PROMPT,
  programTemplateOutputSchema,
} from "./programTemplateDomainRules";

export interface LlmConvertDeps {
  createCompletion: (
    messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
  ) => Promise<OpenAI.Chat.Completions.ChatCompletion>;
}

export type LlmConvertResult =
  | { ok: true; template: NormalizedProgramTemplate }
  | { ok: false; error: string; kind: "parse" | "validation" | "model" };

function stripJsonFence(content: string): string {
  let s = content.trim();
  if (s.startsWith("```")) {
    s = s.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "");
  }
  return s.trim();
}

/** Hard cap so a bad model integer cannot explode memory. */
const MAX_EXPANDED_SETS = 30;

function asNullableNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function asNullableString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function expandIdenticalSets(params: {
  setCount: number;
  setTypeLabel: string | null;
  repMin: number | null;
  repMax: number | null;
  rirGoal: number | null;
  restSeconds: number | null;
}): NormalizedProgramTemplateSet[] {
  const n = Math.min(Math.max(0, Math.floor(params.setCount)), MAX_EXPANDED_SETS);
  const sets: NormalizedProgramTemplateSet[] = [];
  for (let si = 0; si < n; si++) {
    sets.push({
      position: si,
      setTypeLabel: params.setTypeLabel,
      repMin: params.repMin,
      repMax: params.repMax,
      rirGoal: params.rirGoal,
      restSeconds: params.restSeconds,
      isTopSet: si === 0,
    });
  }
  return sets;
}

/**
 * Coerce a loosely-parsed model object into NormalizedProgramTemplate shape.
 * Accepts compact lifts (`setCount` + shared prescription) and expands them server-side.
 * Throws if the top-level shape is unusable.
 */
export function coerceProgramTemplate(raw: unknown): NormalizedProgramTemplate {
  if (!raw || typeof raw !== "object") {
    throw new Error("Model output is not an object");
  }
  const obj = raw as Record<string, unknown>;
  const workoutsIn = Array.isArray(obj.workouts) ? obj.workouts : [];
  const workouts = workoutsIn.map((w, wi) => {
    const wo = (w && typeof w === "object" ? w : {}) as Record<string, unknown>;
    const liftsIn = Array.isArray(wo.lifts) ? wo.lifts : [];
    const lifts = liftsIn.map((l, li) => {
      const lo = (l && typeof l === "object" ? l : {}) as Record<string, unknown>;
      const liftRepMin = asNullableNumber(lo.repMin);
      const liftRepMax = asNullableNumber(lo.repMax);
      const liftRir = asNullableNumber(lo.rirGoal);
      const liftRest = asNullableNumber(lo.restSeconds);
      const liftSetType = asNullableString(lo.setTypeLabel);

      let sets: NormalizedProgramTemplateSet[] = [];

      // Expanded sets array (heterogeneous prescriptions) wins when non-empty.
      if (Array.isArray(lo.sets) && lo.sets.length > 0 && typeof lo.sets[0] === "object") {
        sets = lo.sets.map((s, si) => {
          const so = (s && typeof s === "object" ? s : {}) as Record<string, unknown>;
          return {
            position: typeof so.position === "number" ? so.position : si,
            setTypeLabel: asNullableString(so.setTypeLabel) ?? liftSetType,
            repMin: asNullableNumber(so.repMin) ?? liftRepMin,
            repMax: asNullableNumber(so.repMax) ?? liftRepMax,
            rirGoal: asNullableNumber(so.rirGoal) ?? liftRir,
            restSeconds: asNullableNumber(so.restSeconds) ?? liftRest,
            isTopSet: typeof so.isTopSet === "boolean" ? so.isTopSet : si === 0,
          };
        });
      } else {
        // Compact: setCount / sets-as-number + lift-level prescription.
        let setCount: number | null = null;
        if (typeof lo.setCount === "number" && Number.isFinite(lo.setCount)) {
          setCount = lo.setCount;
        } else if (typeof lo.sets === "number" && Number.isFinite(lo.sets)) {
          setCount = lo.sets;
        } else if (typeof lo.setCount === "string" && lo.setCount.trim() !== "") {
          const parsed = Number(lo.setCount);
          if (Number.isFinite(parsed)) {
            setCount = parsed;
          }
        }
        if (setCount != null && setCount > 0) {
          sets = expandIdenticalSets({
            setCount,
            setTypeLabel: liftSetType,
            repMin: liftRepMin,
            repMax: liftRepMax,
            rirGoal: liftRir,
            restSeconds: liftRest,
          });
        }
      }

      return {
        name: typeof lo.name === "string" ? lo.name : "",
        position: typeof lo.position === "number" ? lo.position : li,
        sets,
        supersetGroupKey: asNullableString(lo.supersetGroupKey),
      };
    });
    return {
      name: typeof wo.name === "string" ? wo.name : `Day ${wi + 1}`,
      weekday: typeof wo.weekday === "string" ? wo.weekday : null,
      position: typeof wo.position === "number" ? wo.position : wi,
      lifts,
      restDay: wo.restDay === true,
    };
  });

  return {
    programName: typeof obj.programName === "string" ? obj.programName : null,
    cycleLength: typeof obj.cycleLength === "number" ? obj.cycleLength : null,
    workouts,
  };
}

function collectValidationErrors(template: NormalizedProgramTemplate): string[] {
  try {
    validateProgramTemplate(template);
    return [];
  } catch (err) {
    return [err instanceof Error ? err.message : String(err)];
  }
}

async function callModel(
  deps: LlmConvertDeps,
  messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
): Promise<string> {
  const completion = await deps.createCompletion(messages);
  const content = completion.choices[0]?.message?.content;
  if (!content) {
    throw new Error("Empty model response");
  }
  return content;
}

/**
 * Convert a preprocessed spreadsheet view into a validated NormalizedProgramTemplate.
 * At most two model calls (initial + one repair).
 */
export async function convertProgramTemplateWithLlm(
  viewText: string,
  deps: LlmConvertDeps,
  options: { fileName?: string | null } = {},
): Promise<LlmConvertResult> {
  if (!viewText.trim()) {
    return { ok: false, error: "Empty spreadsheet view", kind: "parse" };
  }

  const messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [
    { role: "system", content: PROGRAM_TEMPLATE_SYSTEM_PROMPT },
    { role: "user", content: buildProgramTemplateUserPrompt(viewText, options.fileName) },
  ];

  let rawContent: string;
  try {
    rawContent = await callModel(deps, messages);
  } catch (err) {
    return {
      ok: false,
      error: err instanceof Error ? err.message : String(err),
      kind: "model",
    };
  }

  let template: NormalizedProgramTemplate;
  try {
    const parsed = JSON.parse(stripJsonFence(rawContent)) as unknown;
    template = coerceProgramTemplate(parsed);
  } catch (err) {
    return {
      ok: false,
      error: err instanceof Error ? err.message : String(err),
      kind: "parse",
    };
  }

  let errors = collectValidationErrors(template);
  if (errors.length === 0) {
    return { ok: true, template: validateProgramTemplate(template) };
  }

  if (looksLikeNameOnlyShell(template)) {
    console.warn(
      `programTemplateLlmConvert: name-only workout shell (contentLen=${rawContent.length}); attempting repair`,
    );
  }

  // One repair turn.
  messages.push({ role: "assistant", content: rawContent });
  messages.push({
    role: "user",
    content: buildProgramTemplateRepairPrompt(viewText, stripJsonFence(rawContent), errors),
  });

  try {
    rawContent = await callModel(deps, messages);
    const parsed = JSON.parse(stripJsonFence(rawContent)) as unknown;
    template = coerceProgramTemplate(parsed);
  } catch (err) {
    return {
      ok: false,
      error: `Repair failed: ${err instanceof Error ? err.message : String(err)}`,
      kind: "parse",
    };
  }

  errors = collectValidationErrors(template);
  if (errors.length > 0) {
    if (looksLikeNameOnlyShell(template)) {
      console.warn(
        `programTemplateLlmConvert: repair still name-only shell (contentLen=${rawContent.length})`,
      );
    }
    return {
      ok: false,
      error: errors.join("; "),
      kind: "validation",
    };
  }

  return { ok: true, template: validateProgramTemplate(template) };
}

/** True when every non-rest workout has zero lifts — classic reasoning-vs-content failure. */
export function looksLikeNameOnlyShell(template: NormalizedProgramTemplate): boolean {
  const training = template.workouts.filter((w) => !w.restDay);
  return training.length > 0 && training.every((w) => w.lifts.length === 0);
}

/**
 * Build OpenRouter chat completion params (for production wiring).
 */
export function buildProgramTemplateCompletionParams(
  model: string,
  messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
  extras: Record<string, unknown>,
): OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming {
  return {
    model,
    messages,
    temperature: 0.1,
    response_format: {
      type: "json_schema",
      json_schema: programTemplateOutputSchema(),
    },
    ...extras,
  } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming;
}
