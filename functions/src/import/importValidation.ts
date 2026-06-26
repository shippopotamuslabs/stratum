import type { NormalizedHistory, NormalizedSet } from "./schemas";

const MAX_VIOLATIONS_REPORTED = 25;

export function collectImportViolations(history: NormalizedHistory): string[] {
  const violations: string[] = [];
  const sessionIds = new Set(history.sessions.map((session) => session.id));
  const seenSetIds = new Set<string>();

  for (const session of history.sessions) {
    if (session.id.trim().length === 0) {
      violations.push(`Session id must be non-empty.`);
    }
  }

  for (const set of history.sets) {
    violations.push(...collectSetViolations(set, sessionIds, seenSetIds));
    if (violations.length >= MAX_VIOLATIONS_REPORTED) {
      break;
    }
  }

  return violations;
}

function collectSetViolations(
  set: NormalizedSet,
  sessionIds: Set<string>,
  seenSetIds: Set<string>,
): string[] {
  const violations: string[] = [];
  const label = `Set "${set.id}" (session "${set.sessionId}", index ${set.setIndex})`;

  if (set.id.trim().length === 0) {
    violations.push(`${label}: id must be non-empty.`);
  } else if (seenSetIds.has(set.id)) {
    violations.push(`${label}: duplicate set id "${set.id}".`);
  } else {
    seenSetIds.add(set.id);
  }

  if (!sessionIds.has(set.sessionId)) {
    violations.push(`${label}: sessionId "${set.sessionId}" does not match any session.`);
  }

  if (set.exerciseNameRaw.trim().length === 0) {
    violations.push(`${label}: exerciseNameRaw must be non-empty.`);
  }

  if (!Number.isInteger(set.setIndex) || set.setIndex < 0) {
    violations.push(`${label}: setIndex must be a non-negative integer, got ${set.setIndex}.`);
  }

  if (!Number.isFinite(set.weightAmount) || set.weightAmount < 0) {
    violations.push(`${label}: weightAmount must be a non-negative number, got ${set.weightAmount}.`);
  }

  if (!Number.isInteger(set.reps) || set.reps <= 0) {
    violations.push(`${label}: reps must be a positive integer, got ${set.reps}.`);
  }

  if (set.rir !== undefined && (!Number.isInteger(set.rir) || set.rir < 0)) {
    violations.push(`${label}: rir must be a non-negative integer when provided, got ${set.rir}.`);
  }

  return violations;
}

export function formatImportViolations(violations: string[]): string {
  const shown = violations.slice(0, MAX_VIOLATIONS_REPORTED);
  const lines = shown.map((violation) => `- ${violation}`);
  if (violations.length > MAX_VIOLATIONS_REPORTED) {
    lines.push(`- …and ${violations.length - MAX_VIOLATIONS_REPORTED} more`);
  }
  return lines.join("\n");
}

export class ImportValidationError extends Error {
  readonly violations: string[];

  constructor(violations: string[]) {
    super(formatImportViolations(violations));
    this.name = "ImportValidationError";
    this.violations = violations;
  }
}

export function validationErrorToolPayload(violations: string[]): string {
  return JSON.stringify({
    validationErrors: violations.slice(0, MAX_VIOLATIONS_REPORTED),
    totalViolations: violations.length,
    hint:
      "Update the converter script so every set satisfies these rules, then run execute_on_sample before execute_on_full.",
  });
}
