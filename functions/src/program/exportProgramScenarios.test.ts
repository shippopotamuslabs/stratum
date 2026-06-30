import assert from "node:assert/strict";
import { describe, it } from "node:test";
import path from "node:path";
import {
  buildScenarioPayload,
  csvEscape,
  detailCsvToMarkdown,
  exportProgramsToCsv,
  loadLiftCatalog,
  parseCsv,
  rowsToCsv,
} from "./exportProgramScenarios";

describe("exportProgramScenarios", () => {
  it("csvEscape quotes fields with commas", () => {
    assert.equal(csvEscape("hello"), "hello");
    assert.equal(csvEscape('say "hi"'), '"say ""hi"""');
    assert.equal(csvEscape("a,b"), '"a,b"');
  });

  it("rowsToCsv joins rows with newlines", () => {
    assert.equal(rowsToCsv([["a", "b"], ["c", "d"]]), "a,b\nc,d\n");
  });

  it("buildScenarioPayload includes specialization and training days", () => {
    const payload = JSON.parse(
      buildScenarioPayload(
        { id: "t", trainingDaysPerWeek: 4, specialization: { Bicep: "Specialize" } },
        { trainingAge: "Advanced", sessionLengthMinutes: 75 },
        loadLiftCatalog(path.resolve(__dirname, "../../.."), "test"),
      ),
    );
    assert.equal(payload.profile.trainingDaysPerWeek, 4);
    assert.equal(payload.specialization.Bicep, "Specialize");
    assert.ok(payload.provenLifts.length > 0);
  });

  it("detailCsvToMarkdown formats programs for readable review", () => {
    const csv = rowsToCsv([
      ["scenario_id", "scenario_label", "workout", "lift_order", "lift_id", "lift_name", "sets", "rep_min", "rep_max"],
      ["4d-arms", "4-day arm specialization", "Upper A", "1", "curl", "Machine Bicep Curl", "4", "8", "20"],
      ["4d-arms", "4-day arm specialization", "Upper A", "2", "bench", "Barbell Bench Press", "3", "4", "12"],
      ["4d-arms", "4-day arm specialization", "Lower A", "1", "squat", "Front Squat", "4", "4", "8"],
    ]);
    const md = detailCsvToMarkdown(csv);
    assert.ok(md.includes("# 4-day arm specialization"));
    assert.ok(md.includes("## Upper A"));
    assert.ok(md.includes("Machine Bicep Curl — 4 × 8–20"));
    assert.ok(md.includes("## Lower A"));
  });

  it("parseCsv handles quoted fields", () => {
    const rows = parseCsv('a,b\n"hello, world",2\n');
    assert.deepEqual(rows, [["a", "b"], ["hello, world", "2"]]);
  });

  it("exportProgramsToCsv writes detail and summary files", async () => {
    const repoRoot = path.resolve(__dirname, "../../..");
    const outDir = path.join(repoRoot, "functions/out/test-export");
    const outputPath = path.join(outDir, "programs.csv");

    const result = await exportProgramsToCsv({
      repoRoot,
      batch: {
        scenarios: [
          {
            id: "4d-arms",
            label: "4-day arms",
            trainingDaysPerWeek: 4,
            specialization: { Bicep: "Specialize", Tricep: "Specialize" },
          },
        ],
      },
      outputPath,
    });

    assert.equal(result.scenarioCount, 1);
    assert.ok(result.detailRowCount > 10);
    assert.ok(result.summaryRowCount >= 4);
    const detail = rowsToCsv([]); // noop — verify file exists via read
    const fs = await import("node:fs");
    const content = fs.readFileSync(result.outputPath, "utf8");
    assert.ok(content.includes("scenario_id"));
    assert.ok(content.includes("4d-arms"));
    assert.ok(fs.existsSync(result.summaryPath));
    assert.ok(fs.existsSync(result.markdownPath));
    const md = fs.readFileSync(result.markdownPath, "utf8");
    assert.ok(md.includes("# 4-day arms"));
    void detail;
  });
});
