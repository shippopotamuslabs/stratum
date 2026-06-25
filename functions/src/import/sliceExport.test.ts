import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildFullSlice, limitSamplePreview } from "./sliceExport";
import { FakeToolStorage } from "./toolStorage";

describe("limitSamplePreview", () => {
  it("returns full text when under row limit", () => {
    const text = "a\nb\nc";
    assert.equal(limitSamplePreview(text), text);
  });

  it("truncates to first 30 lines", () => {
    const lines = Array.from({ length: 50 }, (_, i) => `row-${i}`);
    const preview = limitSamplePreview(lines.join("\n"));
    assert.equal(preview.split("\n").length, 30);
    assert.equal(preview.startsWith("row-0"), true);
    assert.ok(!preview.includes("row-49"));
  });
});

describe("buildFullSlice", () => {
  it("persists sliced CSV to storage", async () => {
    const storage = new FakeToolStorage();
    const csv = [
      "exercise_name,weight,reps,timestamp",
      "Squat,100,8,2025-06-01T10:00:00Z",
      "Squat,105,8,2025-06-02T10:00:00Z",
    ].join("\n");
    const path = "imports/user/job/slice/full.csv";
    await buildFullSlice(csv, path, storage.bucket());
    assert.ok(storage.files.has(path));
    const saved = storage.files.get(path)!.toString("utf8");
    assert.ok(saved.includes("exercise_name"));
  });

  it("passes through CSV without date column unchanged", async () => {
    const storage = new FakeToolStorage();
    const csv = "name,value\nSquat,100";
    const path = "imports/user/job/slice/full.csv";
    await buildFullSlice(csv, path, storage.bucket());
    assert.equal(storage.files.get(path)!.toString("utf8"), csv);
  });

  it("truncates large JSON arrays", async () => {
    const storage = new FakeToolStorage();
    const items = Array.from({ length: 600 }, (_, i) => ({ id: i }));
    const json = JSON.stringify(items);
    const path = "imports/user/job/slice/full.json";
    await buildFullSlice(json, path, storage.bucket());
    const saved = JSON.parse(storage.files.get(path)!.toString("utf8")) as unknown[];
    assert.equal(saved.length, 500);
  });
});
