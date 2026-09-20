import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { normalizeWorkbook } from "./workbookNormalize";
import { selectRegionRowIndices, sliceProgramRegion } from "./sliceProgramRegion";

describe("sliceProgramRegion", () => {
  it("slices to Week 1 only on multi-week tables", async () => {
    const csv = [
      "Section,Exercise,Sets,Reps",
      "Week 1,Exercise,,",
      "Full Body 1,Back squat,3,5",
      ",Overhead press,3,8",
      "Full Body 2,Deadlift,3,4",
      "Week 2,Exercise,,",
      "Lower #1,Deadlift,3,3",
      ",Leg curl,3,8",
      "Week 3,Exercise,,",
      "Full Body 1,Back squat,3,5",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const sheet = wb.sheets[0]!;
    const { indices, weekSliced, regionLabel } = selectRegionRowIndices(sheet);
    assert.equal(weekSliced, true);
    assert.ok(regionLabel.includes("Week 1"));
    const text = indices.map((i) => sheet.rows[i]!.join(" ")).join("\n");
    assert.ok(text.includes("Back squat"));
    assert.ok(text.includes("Deadlift"));
    assert.ok(!text.includes("Leg curl"), "Week 2 lifts must be excluded");
    assert.ok(!text.includes("Week 3"));
  });

  it("stops before volume summary on tidy personal sheets", async () => {
    const csv = [
      "Day,Lift Name,Sets,Rep Range",
      "Upper A,Bench Press,4,8-10",
      ",Row,3,8-10",
      "Lower A,Squat,3,5",
      ",,",
      "Muscle Group,Volume,,",
      "Chest,20,,",
      "Back,10,,",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const sliced = sliceProgramRegion(wb, 0);
    assert.ok(sliced.viewText.includes("Bench Press"));
    assert.ok(!sliced.viewText.includes("Muscle Group"));
    assert.ok(sliced.liftRowEstimate >= 2);
  });

  it("includes multi-week note when week-sliced", async () => {
    const csv = [
      "A,B,C",
      "Week 1,Exercise,",
      "Day 1,Squat,3",
      "Week 2,Exercise,",
      "Day 1,Bench,3",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const sliced = sliceProgramRegion(wb, 0);
    assert.equal(sliced.weekSliced, true);
    assert.ok(sliced.viewText.includes("Multi-week"));
  });
});
