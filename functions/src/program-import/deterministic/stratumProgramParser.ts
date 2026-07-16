import type { NormalizedProgramTemplate, NormalizedProgramTemplateLift, NormalizedProgramTemplateSet, NormalizedProgramTemplateWorkout } from "./types";

/**
 * Stratum program export envelope detector/parser.
 * Match requires schema + source === "stratum" + schemaVersion === 1.
 */

function tryParseEnvelope(text: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(text);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function matchesStratumProgramExport(text: string): boolean {
  const obj = tryParseEnvelope(text);
  if (!obj) return false;
  return (
    obj.schema === "stratum.export.programs" &&
    obj.source === "stratum" &&
    obj.schemaVersion === 1
  );
}

export function parseStratumProgramExport(text: string): {
  programs: Array<{ id: string | null; template: NormalizedProgramTemplate }>;
} {
  const obj = tryParseEnvelope(text);
  if (!obj) {
    throw new Error("Invalid Stratum program export JSON");
  }
  if (
    obj.schema !== "stratum.export.programs" ||
    obj.source !== "stratum" ||
    obj.schemaVersion !== 1
  ) {
    throw new Error("Not a Stratum program export envelope");
  }
  const rawPrograms = Array.isArray(obj.programs) ? obj.programs : [];
  const programs = rawPrograms.map((entry, index) => {
    if (!entry || typeof entry !== "object") {
      throw new Error(`Invalid program at index ${index}`);
    }
    const p = entry as Record<string, unknown>;
    const id = typeof p.id === "string" ? p.id : null;
    let templateObj: Record<string, unknown>;
    if (p.template && typeof p.template === "object" && !Array.isArray(p.template)) {
      templateObj = p.template as Record<string, unknown>;
    } else {
      // Flat shape: template fields at program root
      templateObj = p;
    }
    return {
      id,
      template: parseTemplate(templateObj),
    };
  });
  return { programs };
}

function parseTemplate(obj: Record<string, unknown>): NormalizedProgramTemplate {
  const rawWorkouts = Array.isArray(obj.workouts) ? obj.workouts : [];
  const workouts = rawWorkouts.map((w, wi) => parseWorkout(w, wi));
  return {
    programName: typeof obj.programName === "string" ? obj.programName : null,
    cycleLength: typeof obj.cycleLength === "number" ? obj.cycleLength : null,
    workouts,
  };
}

function parseWorkout(entry: unknown, fallbackIndex: number): NormalizedProgramTemplateWorkout {
  if (!entry || typeof entry !== "object") {
    throw new Error(`Invalid workout at index ${fallbackIndex}`);
  }
  const w = entry as Record<string, unknown>;
  const rawLifts = Array.isArray(w.lifts) ? w.lifts : [];
  return {
    name: typeof w.name === "string" ? w.name : "",
    weekday: typeof w.weekday === "string" ? w.weekday : null,
    position: typeof w.position === "number" ? w.position : fallbackIndex,
    restDay: w.restDay === true,
    lifts: rawLifts.map((l, li) => parseLift(l, li)),
  };
}

function parseLift(entry: unknown, fallbackIndex: number): NormalizedProgramTemplateLift {
  if (!entry || typeof entry !== "object") {
    throw new Error(`Invalid lift at index ${fallbackIndex}`);
  }
  const l = entry as Record<string, unknown>;
  const rawSets = Array.isArray(l.sets) ? l.sets : [];
  return {
    name: typeof l.name === "string" ? l.name : "",
    position: typeof l.position === "number" ? l.position : fallbackIndex,
    sets: rawSets.map((s, si) => parseSet(s, si)),
    supersetGroupKey: typeof l.supersetGroupKey === "string" ? l.supersetGroupKey : null,
  };
}

function parseSet(entry: unknown, fallbackIndex: number): NormalizedProgramTemplateSet {
  if (!entry || typeof entry !== "object") {
    throw new Error(`Invalid set at index ${fallbackIndex}`);
  }
  const s = entry as Record<string, unknown>;
  return {
    position: typeof s.position === "number" ? s.position : fallbackIndex,
    setTypeLabel: typeof s.setTypeLabel === "string" ? s.setTypeLabel : null,
    repMin: typeof s.repMin === "number" ? s.repMin : null,
    repMax: typeof s.repMax === "number" ? s.repMax : null,
    rirGoal: typeof s.rirGoal === "number" ? s.rirGoal : null,
    restSeconds: typeof s.restSeconds === "number" ? s.restSeconds : null,
    isTopSet: s.isTopSet === true,
  };
}
