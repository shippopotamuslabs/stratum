/**
 * Single-shot (optional one repair) OpenRouter conversion of a spreadsheet view → NormalizedProgramTemplate.
 */
import OpenAI from "openai";
import type { NormalizedProgramTemplate } from "../deterministic/types";
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

/**
 * Coerce a loosely-parsed model object into NormalizedProgramTemplate shape.
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
      const setsIn = Array.isArray(lo.sets) ? lo.sets : [];
      const sets = setsIn.map((s, si) => {
        const so = (s && typeof s === "object" ? s : {}) as Record<string, unknown>;
        return {
          position: typeof so.position === "number" ? so.position : si,
          setTypeLabel: typeof so.setTypeLabel === "string" ? so.setTypeLabel : null,
          repMin: typeof so.repMin === "number" ? so.repMin : null,
          repMax: typeof so.repMax === "number" ? so.repMax : null,
          rirGoal: typeof so.rirGoal === "number" ? so.rirGoal : null,
          restSeconds: typeof so.restSeconds === "number" ? so.restSeconds : null,
          isTopSet: typeof so.isTopSet === "boolean" ? so.isTopSet : si === 0,
        };
      });
      return {
        name: typeof lo.name === "string" ? lo.name : "",
        position: typeof lo.position === "number" ? lo.position : li,
        sets,
        supersetGroupKey:
          typeof lo.supersetGroupKey === "string" ? lo.supersetGroupKey : null,
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
    return {
      ok: false,
      error: errors.join("; "),
      kind: "validation",
    };
  }

  return { ok: true, template: validateProgramTemplate(template) };
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
