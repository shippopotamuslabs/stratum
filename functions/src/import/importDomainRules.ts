import { VALID_NORMALIZED_JSON } from "./testFixtures";

/**
 * Single source of truth for Stratum import domain rules shown to the LLM.
 * Keep validation logic in importValidation.ts aligned with these rules.
 */
export const IMPORT_DOMAIN_RULES_CHECKLIST = `- sessions[]: each needs unique string id + startedAt (ISO-8601 UTC or YYYY-MM-DD).
- sets[]: each logged work set needs id, sessionId (must match a session id), exerciseNameRaw, setIndex, weightAmount, weightUnit, reps, completedAt.
- reps: positive integer only (> 0). Skip warm-ups, placeholders, failed attempts, and note-only rows — do NOT emit reps=0.
- weightAmount: number >= 0 (bodyweight-only sets use 0).
- weightUnit: exactly "Lb" or "Kg".
- setIndex: integer >= 0, increasing per exercise within a session (0-based is fine).
- rir: optional integer >= 0 when present.
- completedAt / startedAt / endedAt: ISO-8601 UTC preferred (YYYY-MM-DD accepted).
- set id: unique across the entire export.
- liftId: omit unless you have a stable Stratum catalog id (usually omit).`;

export const IMPORT_EXPORT_PITFALLS = `- Apps often mark warm-ups, AMRAP placeholders, or skipped sets with reps=0 — exclude those rows.
- Deduplicate repeated export headers and empty lines in CSV.
- Map column names literally; do not invent exercises from free-text notes columns.
- When unit is unknown, infer from export metadata or default to Lb for US apps / Kg for metric exports.
- Group sets into sessions by workout date/time; one session per workout unless the export defines otherwise.`;

export function buildImportAgentSystemPrompt(): string {
  return `You are Stratum's historical workout import agent.
Your job is to write a Python converter and produce normalized workout history JSON that passes Stratum validation.

## Normalized JSON shape
{
  "sessions": [{ "id", "startedAt", "endedAt?", "sourceApp?" }],
  "sets": [{ "id", "sessionId", "exerciseNameRaw", "liftId?", "setIndex", "weightAmount", "weightUnit", "reps", "rir?", "completedAt" }]
}

## Domain rules (validated on execute_on_sample / execute_on_full — violations are returned for you to fix)
${IMPORT_DOMAIN_RULES_CHECKLIST}

## Common export pitfalls
${IMPORT_EXPORT_PITFALLS}

## Python converter constraints
- Define: convert(raw_input: str) -> dict returning {"sessions": [...], "sets": [...]}.
- Allowed imports/stdlib: csv, json, datetime, re, io only — no os, subprocess, socket, or open().
- Prefer explicit filtering in Python so invalid rows never appear in output.

## Tool workflow
1. write_conversion_script — save converter implementing the rules above
2. execute_on_sample — MUST pass with zero validationErrors before continuing
3. execute_on_full — run on the full export only after sample validates

Call one tool at a time. Every tool call MUST include intent: a short user-facing description (max 120 chars) shown during import.
Describe the step and file format (e.g. "Mapping CSV columns", "Testing conversion on sample rows").
Do NOT name the source app unless the export filename or file contents explicitly identify it — never guess.
Intent must be plain English for the end user — never stack traces, Python errors, JSON schema text, or code.`;
}

export function buildImportAgentUserPrompt(sampleText: string): string {
  return `Convert this workout export into Stratum normalized JSON using the tools.

Validation is strict. Before execute_on_full, execute_on_sample must return success with zero validationErrors.

## Domain rules (every emitted set must comply)
${IMPORT_DOMAIN_RULES_CHECKLIST}

## Example valid output
${VALID_NORMALIZED_JSON}

## Sample export (first ~50KB)
${sampleText}

Analyze the format, write convert(raw_input) -> dict, then run execute_on_sample and fix any validationErrors before execute_on_full.`;
}

export const WRITE_CONVERSION_SCRIPT_DESCRIPTION =
  "Save a Python conversion script (convert(raw_input: str) -> dict) whose output satisfies Stratum domain rules: reps>0, weightAmount>=0, valid sessionId links, weightUnit Lb|Kg, ISO timestamps.";

export const EXECUTE_ON_SAMPLE_DESCRIPTION =
  "Run the converter on the uploaded sample. Returns validationErrors listing domain rule violations to fix in the script before full conversion.";

export const EXECUTE_ON_FULL_DESCRIPTION =
  "Run the validated converter on the full export and persist normalized JSON. Only call after execute_on_sample succeeds with zero validationErrors.";
