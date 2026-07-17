import type { NormalizedHistory, NormalizedSession, NormalizedSet } from "../schemas";

/**
 * Stratum log export envelope detector/parser.
 * Match requires schema + source === "stratum" + schemaVersion === 1.
 * Preserves session/set ids from envelope.history verbatim.
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

export function matchesStratumLogExport(text: string): boolean {
  const obj = tryParseEnvelope(text);
  if (!obj) return false;
  return (
    obj.schema === "stratum.export.logs" &&
    obj.source === "stratum" &&
    obj.schemaVersion === 1
  );
}

export function parseStratumLogExport(text: string): NormalizedHistory {
  const obj = tryParseEnvelope(text);
  if (!obj) {
    throw new Error("Invalid Stratum log export JSON");
  }
  if (
    obj.schema !== "stratum.export.logs" ||
    obj.source !== "stratum" ||
    obj.schemaVersion !== 1
  ) {
    throw new Error("Not a Stratum log export envelope");
  }
  const history = obj.history;
  if (!history || typeof history !== "object" || Array.isArray(history)) {
    throw new Error("Stratum log export missing history");
  }
  const hist = history as Record<string, unknown>;
  const rawSessions = Array.isArray(hist.sessions) ? hist.sessions : [];
  const rawSets = Array.isArray(hist.sets) ? hist.sets : [];

  const sessions: NormalizedSession[] = rawSessions.map((entry, index) => {
    if (!entry || typeof entry !== "object") {
      throw new Error(`Invalid session at index ${index}`);
    }
    const s = entry as Record<string, unknown>;
    if (typeof s.id !== "string" || typeof s.startedAt !== "string") {
      throw new Error(`Session ${index} requires id and startedAt`);
    }
    const session: NormalizedSession = {
      id: s.id,
      startedAt: s.startedAt,
    };
    if (typeof s.endedAt === "string") {
      session.endedAt = s.endedAt;
    }
    if (typeof s.sourceApp === "string") {
      session.sourceApp = s.sourceApp;
    }
    return session;
  });

  const sets: NormalizedSet[] = rawSets.map((entry, index) => {
    if (!entry || typeof entry !== "object") {
      throw new Error(`Invalid set at index ${index}`);
    }
    const row = entry as Record<string, unknown>;
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
      throw new Error(`Set ${index} missing required fields`);
    }
    const set: NormalizedSet = {
      id: row.id,
      sessionId: row.sessionId,
      exerciseNameRaw: row.exerciseNameRaw,
      setIndex: row.setIndex,
      weightAmount: row.weightAmount,
      weightUnit: row.weightUnit,
      reps: row.reps,
      completedAt: row.completedAt,
    };
    if (typeof row.liftId === "string") {
      set.liftId = row.liftId;
    }
    if (typeof row.rir === "number") {
      set.rir = row.rir;
    }
    return set;
  });

  return { sessions, sets };
}
