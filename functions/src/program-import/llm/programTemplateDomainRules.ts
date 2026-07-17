/**
 * System / user prompts for custom program-template LLM conversion.
 * Keep aligned with validateProgramTemplate in deterministic/types.ts.
 *
 * Prefer compact lift rows (setCount + shared prescription). The server expands
 * identical working sets — models that "reason correctly" then emit empty shells
 * under full set-expansion JSON fail production jobs (see 5-Day Hypertrophy).
 */

export const PROGRAM_TEMPLATE_LLM_PROMPT_VERSION = 2;

export const PROGRAM_TEMPLATE_SYSTEM_PROMPT = `You convert workout program spreadsheets into Stratum NormalizedProgramTemplate JSON.

## Output shape (JSON only — no markdown fences, no commentary)
{
  "programName": string | null,
  "cycleLength": number | null,
  "workouts": [
    {
      "name": string,
      "weekday": string | null,
      "position": number,
      "restDay": boolean | optional,
      "lifts": [
        {
          "name": string,
          "position": number,
          "supersetGroupKey": string | null,
          "setCount": number,
          "repMin": number | null,
          "repMax": number | null,
          "rirGoal": number | null,
          "restSeconds": number | null,
          "setTypeLabel": string | null
        }
      ]
    }
  ]
}

## Critical completeness rule
- Every non-rest workout MUST include a non-empty "lifts" array with real exercises from the sheet.
- Emitting only workout names (name + weekday) is INVALID. Put lifts in the JSON content, not only in your private reasoning.
- Prefer the compact lift shape above. The server expands setCount into individual set objects.

## Mapping rules
1. Compact sets (preferred): if a row says 4 sets of 8-10, emit ONE lift with setCount=4, repMin=8, repMax=10. Do NOT repeat the same set object N times unless set prescriptions differ within the lift.
2. Heterogeneous sets only: if top set / backoff differ (e.g. 1×5 then 3×8), you may emit "sets": [{repMin, repMax, ...}, ...] instead of setCount. First set isTopSet=true when expanded server-side from setCount; for explicit sets arrays set isTopSet on the first object true and others false.
3. Day labels may be sparse or mid-block. Group contiguous lifts into workouts using day names, movement patterns, and upper/lower structure. Do NOT orphan lifts that appear before the first day label when they clearly belong to that block.
4. Ignore volume tables, lookup sheets, copyright banners, long essays, 1RM inputs, load/%1RM formulas, and empty accessory placeholders.
5. The view may already be sliced to Week 1 of a multi-week mesocycle. Import that week as a repeating template — do not invent later weeks.
6. Warm-up set columns are NOT working sets. Prefer "Working Sets" when both warm-up and working columns exist.
7. Merge consecutive rows with the same exercise name (top set + backoff) into one lift with multiple prescriptions (heterogeneous sets or one compact row when identical).
8. RPE → rirGoal ≈ max(0, round(10 − RPE)) when RPE is numeric; otherwise null. Do not invent RIR.
9. Rest text: "3-4 min" → mid of range in seconds (210); "30sec" → 30; unknown → null.
10. Superset prefixes like "A1:" / "B2." → supersetGroupKey "A" or "B" (letter group); strip the prefix from the lift name.
11. Exercise alternatives joined by " OR " → take the first option only.
12. Non-numeric intensity labels (Myo-Match, AMRAP, Myo-reps, Long-length Partials) → setTypeLabel; repMin/repMax null when not numeric.
13. Weekday in parentheses e.g. "Upper A (Sunday)" → weekday "Sunday" and a clear workout name.
14. Explicit rest day lines → workout with restDay=true and empty lifts.
15. cycleLength: MUST be the number of ordered day rows in the cycle (training days + restDay rows). Use 7 when the week has named weekdays or rest days and forms a full week; otherwise the day-row count. NEVER use mesocycle/week-block counts (e.g. MacroFactor "Cycles: 9" or "4 weeks") as cycleLength.
16. programName: from filename/title if present, else a short descriptive name.
17. Every non-rest workout needs ≥1 lift; every lift needs setCount≥1 (or a non-empty sets array); lift names non-empty; if both repMin and repMax present then repMax ≥ repMin and both > 0.
18. Keep JSON short: compact lifts, no prose, no duplicated set objects for identical working sets.

Return ONLY the JSON object.`;

export function buildProgramTemplateUserPrompt(viewText: string, fileName?: string | null): string {
  const nameLine = fileName ? `Original file name: ${fileName}\n` : "";
  return `${nameLine}Convert this program spreadsheet view into NormalizedProgramTemplate JSON.
Use compact lifts (setCount + repMin/repMax). Include every lift under its workout — do not return name-only workout shells.

## Spreadsheet view
${viewText}
`;
}

export function buildProgramTemplateRepairPrompt(
  viewText: string,
  previousJson: string,
  validationErrors: string[],
): string {
  return `Your previous JSON failed validation. Fix it and return ONLY corrected NormalizedProgramTemplate JSON.

Common failure: workouts with empty or missing "lifts". Re-read the spreadsheet and include every exercise under the correct day. Prefer compact lifts: setCount + repMin/repMax (server expands sets). Do not emit name-only workout shells.

## Validation errors
${validationErrors.map((e) => `- ${e}`).join("\n")}

## Previous JSON
${previousJson}

## Spreadsheet view (same as before)
${viewText}
`;
}

/**
 * JSON schema for OpenRouter response_format.
 * Compact lift fields are first-class; expanded sets remain allowed for heterogeneous rows.
 * Completeness is still enforced server-side (rest days may have empty lifts).
 */
export function programTemplateOutputSchema() {
  return {
    name: "normalized_program_template",
    strict: false,
    schema: {
      type: "object",
      properties: {
        programName: { type: ["string", "null"] },
        cycleLength: { type: ["integer", "null"] },
        workouts: {
          type: "array",
          items: {
            type: "object",
            properties: {
              name: { type: "string" },
              weekday: { type: ["string", "null"] },
              position: { type: "integer" },
              restDay: { type: "boolean" },
              lifts: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    name: { type: "string" },
                    position: { type: "integer" },
                    supersetGroupKey: { type: ["string", "null"] },
                    setCount: { type: "integer" },
                    repMin: { type: ["integer", "null"] },
                    repMax: { type: ["integer", "null"] },
                    rirGoal: { type: ["integer", "null"] },
                    restSeconds: { type: ["integer", "null"] },
                    setTypeLabel: { type: ["string", "null"] },
                    sets: {
                      type: "array",
                      items: {
                        type: "object",
                        properties: {
                          position: { type: "integer" },
                          setTypeLabel: { type: ["string", "null"] },
                          repMin: { type: ["integer", "null"] },
                          repMax: { type: ["integer", "null"] },
                          rirGoal: { type: ["integer", "null"] },
                          restSeconds: { type: ["integer", "null"] },
                          isTopSet: { type: "boolean" },
                        },
                      },
                    },
                  },
                  required: ["name"],
                },
              },
            },
            required: ["name", "lifts"],
          },
        },
      },
      required: ["workouts"],
    },
  };
}
