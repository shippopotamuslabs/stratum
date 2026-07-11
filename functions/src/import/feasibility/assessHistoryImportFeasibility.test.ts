import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { assessHistoryImportFeasibility } from "./assessHistoryImportFeasibility";

describe("assessHistoryImportFeasibility", () => {
  it("rejects empty uploads", () => {
    const result = assessHistoryImportFeasibility(Buffer.alloc(0));
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "empty");
  });

  it("rejects whitespace-only text", () => {
    const result = assessHistoryImportFeasibility(Buffer.from("   \n\t\n  ", "utf8"));
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "empty");
  });

  it("rejects PDF magic bytes", () => {
    const result = assessHistoryImportFeasibility(Buffer.from("%PDF-1.4 fake content", "utf8"));
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "binary_noise");
  });

  it("rejects PNG magic bytes", () => {
    const result = assessHistoryImportFeasibility(
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00]),
    );
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "binary_noise");
  });

  it("rejects high-null binary noise", () => {
    const buf = Buffer.alloc(200, 0);
    buf.write("xx", 0);
    const result = assessHistoryImportFeasibility(buf);
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "binary_noise");
  });

  it("rejects a pure prose essay with no set-like structure", () => {
    const essay = Array.from({ length: 20 }, (_, i) =>
      `Chapter ${i}: Once upon a time the kingdom flourished under wise rule.`,
    ).join("\n");
    const result = assessHistoryImportFeasibility(Buffer.from(essay, "utf8"));
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "no_structure");
  });

  it("accepts a Hevy-like CSV header", () => {
    const csv = [
      "title,start_time,end_time,exercise_title,set_index,weight_kg,reps",
      "Push,2024-01-01T10:00:00,2024-01-01T11:00:00,Bench Press,0,100,5",
      "Push,2024-01-01T10:00:00,2024-01-01T11:00:00,Bench Press,1,100,5",
    ].join("\n");
    const result = assessHistoryImportFeasibility(Buffer.from(csv, "utf8"));
    assert.equal(result.decision, "accept");
    assert.equal(result.reasonCode, "ok");
  });

  it("accepts a Strong-like semicolon CSV", () => {
    const csv = [
      "Date;Workout Name;Exercise Name;Set Order;Weight;Reps;RPE",
      "2024-01-01;A;Squat;1;140;5;8",
      "2024-01-01;A;Squat;2;140;5;8",
    ].join("\n");
    const result = assessHistoryImportFeasibility(Buffer.from(csv, "utf8"));
    assert.equal(result.decision, "accept");
  });

  it("accepts numeric tabular data without English headers", () => {
    // Unknown export: rows of numbers should still reach the agent.
    const rows = [
      "foo,bar,baz",
      "1,2,3",
      "4,5,6",
      "7,8,9",
    ].join("\n");
    const result = assessHistoryImportFeasibility(Buffer.from(rows, "utf8"));
    assert.equal(result.decision, "accept");
  });

  it("accepts XLSX ZIP local-file header (PK\\x03\\x04) without agent pre-reject", () => {
    const xlsxLike = Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00]);
    const result = assessHistoryImportFeasibility(xlsxLike);
    assert.equal(result.decision, "accept");
    assert.equal(result.reasonCode, "ok");
  });
});
