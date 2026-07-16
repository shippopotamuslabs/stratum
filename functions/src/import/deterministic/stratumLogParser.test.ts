import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, sep } from "node:path";
import { matchesStratumLogExport, parseStratumLogExport } from "./stratumLogParser";
import { detectAndParse } from "./formatDetector";

const SRC_DIR = __dirname.replace(`${sep}lib`, `${sep}src`);
const FIXTURES = join(SRC_DIR, "fixtures");

const FIX_STRATUM_LOGS = readFileSync(join(FIXTURES, "stratum_logs_v1.json"), "utf8");

describe("stratumLogParser", () => {
  it("IE-S3-001 Stratum log fixture detects as stratum", () => {
    const result = detectAndParse(Buffer.from(FIX_STRATUM_LOGS, "utf8"), "stratum-logs.json");
    assert.ok(result);
    assert.equal(result!.format, "stratum");
  });

  it("IE-S3-002 Stratum log parse preserves ids", () => {
    const history = parseStratumLogExport(FIX_STRATUM_LOGS);
    assert.equal(history.sessions[0].id, "sess-export-1");
    assert.equal(history.sets[0].id, "set-export-1");
    assert.equal(history.sets[0].sessionId, "sess-export-1");
  });

  it("IE-S3-003 Multi-session Stratum log preserves both session ids", () => {
    const multi = {
      schema: "stratum.export.logs",
      schemaVersion: 1,
      source: "stratum",
      exportedAt: "2026-07-16T15:00:00Z",
      range: { kind: "all_time" },
      history: {
        sessions: [
          { id: "sess-1", startedAt: "2026-07-01T14:00:00Z" },
          { id: "sess-2", startedAt: "2026-07-02T14:00:00Z" },
        ],
        sets: [
          {
            id: "set-1",
            sessionId: "sess-1",
            exerciseNameRaw: "Squat",
            setIndex: 0,
            weightAmount: 100,
            weightUnit: "Lb",
            reps: 5,
            completedAt: "2026-07-01T14:05:00Z",
          },
          {
            id: "set-2",
            sessionId: "sess-2",
            exerciseNameRaw: "Bench",
            setIndex: 0,
            weightAmount: 60,
            weightUnit: "Kg",
            reps: 8,
            completedAt: "2026-07-02T14:05:00Z",
          },
        ],
      },
    };
    const history = parseStratumLogExport(JSON.stringify(multi));
    const ids = history.sessions.map((s) => s.id).sort();
    assert.deepEqual(ids, ["sess-1", "sess-2"]);
  });

  it("IE-S3-M001 Mutation defense requires source stratum", () => {
    const partial = JSON.stringify({
      schema: "stratum.export.logs",
      schemaVersion: 1,
      source: "not-stratum",
      history: { sessions: [], sets: [] },
    });
    assert.equal(matchesStratumLogExport(partial), false);
  });
});

describe("formatDetector stratum regression", () => {
  it("IE-S3-006 Third-party Hevy CSV still detects as hevy", () => {
    const HEVY =
      "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n" +
      'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Crunch (Machine),,,"0","normal",205,18,,,9\n';
    const result = detectAndParse(Buffer.from(HEVY, "utf8"), "hevy.csv");
    assert.ok(result);
    assert.equal(result!.format, "hevy");
  });

  it("IE-S3-007 Unknown JSON without stratum markers returns null", () => {
    const result = detectAndParse(Buffer.from('{"foo":1}', "utf8"), "unknown.json");
    assert.equal(result, null);
  });
});
