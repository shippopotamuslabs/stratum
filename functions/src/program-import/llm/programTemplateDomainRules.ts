/**
 * System / user prompts for custom program-template LLM conversion.
 * Keep aligned with validateProgramTemplate in deterministic/types.ts.
 */

export const PROGRAM_TEMPLATE_LLM_PROMPT_VERSION = 1;

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
          "sets": [
            {
              "position": number,
              "setTypeLabel": string | null,
              "repMin": number | null,
              "repMax": number | null,
              "rirGoal": number | null,
              "restSeconds": number | null,
              "isTopSet": boolean
            }
          ]
        }
      ]
    }
  ]
}

## Mapping rules
1. Expand set counts: if a row says 4 sets of 8-10, emit 4 set objects each with repMin=8, repMax=10. First set isTopSet=true; others false.
2. Day labels may be sparse or mid-block. Group contiguous lifts into workouts using day names, movement patterns, and upper/lower structure. Do NOT orphan lifts that appear before the first day label when they clearly belong to that block.
3. Ignore volume tables, lookup sheets, copyright banners, long essays, 1RM inputs, load/%1RM formulas, and empty accessory placeholders.
4. The view may already be sliced to Week 1 of a multi-week mesocycle. Import that week as a repeating template — do not invent later weeks.
5. Warm-up set columns are NOT working sets. Prefer "Working Sets" when both warm-up and working columns exist.
6. Merge consecutive rows with the same exercise name (top set + backoff) into one lift with multiple sets/prescriptions.
7. RPE → rirGoal ≈ max(0, round(10 − RPE)) when RPE is numeric; otherwise null. Do not invent RIR.
8. Rest text: "3-4 min" → mid of range in seconds (210); "30sec" → 30; unknown → null.
9. Superset prefixes like "A1:" / "B2." → supersetGroupKey "A" or "B" (letter group); strip the prefix from the lift name.
10. Exercise alternatives joined by " OR " → take the first option only.
11. Non-numeric intensity labels (Myo-Match, AMRAP, Myo-reps, Long-length Partials) → setTypeLabel; repMin/repMax null when not numeric.
12. Weekday in parentheses e.g. "Upper A (Sunday)" → weekday "Sunday" and a clear workout name.
13. Explicit rest day lines → workout with restDay=true and empty lifts.
14. cycleLength: MUST be the number of ordered day rows in the cycle (training days + restDay rows). Use 7 when the week has named weekdays or rest days and forms a full week; otherwise the day-row count. NEVER use mesocycle/week-block counts (e.g. MacroFactor "Cycles: 9" or "4 weeks") as cycleLength.
15. programName: from filename/title if present, else a short descriptive name.
16. Every non-rest workout needs ≥1 lift; every lift needs ≥1 set; lift names non-empty; if both repMin and repMax present then repMax ≥ repMin and both > 0.

Return ONLY the JSON object.`;

export function buildProgramTemplateUserPrompt(viewText: string, fileName?: string | null): string {
  const nameLine = fileName ? `Original file name: ${fileName}\n` : "";
  return `${nameLine}Convert this program spreadsheet view into NormalizedProgramTemplate JSON.

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

## Validation errors
${validationErrors.map((e) => `- ${e}`).join("\n")}

## Previous JSON
${previousJson}

## Spreadsheet view (same as before)
${viewText}
`;
}

/** JSON schema for OpenRouter response_format (lenient — validation is server-side). */
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
                },
              },
            },
          },
        },
      },
      required: ["workouts"],
    },
  };
}
