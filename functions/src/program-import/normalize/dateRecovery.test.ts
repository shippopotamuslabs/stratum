import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  excelSerialToUtcDate,
  isMonthDayOnlyFormat,
  recoverMonthDayRepRange,
} from "./dateRecovery";

describe("dateRecovery", () => {
  it("detects month-day-only formats", () => {
    assert.equal(isMonthDayOnlyFormat("m-d"), true);
    assert.equal(isMonthDayOnlyFormat("m\\-d"), true);
    assert.equal(isMonthDayOnlyFormat("m/d"), true);
    assert.equal(isMonthDayOnlyFormat("yyyy-mm-dd"), false);
    assert.equal(isMonthDayOnlyFormat("m/d/yy"), false);
    assert.equal(isMonthDayOnlyFormat("h:mm"), false);
    assert.equal(isMonthDayOnlyFormat(undefined), false);
  });

  it("recovers 8-10 from a local August 10 Date (SheetJS-style)", () => {
    // Local calendar constructor — matches SheetJS cellDates midnight locals.
    const d = new Date(2025, 7, 10); // month 7 = August
    assert.equal(recoverMonthDayRepRange(d, "m-d", "local"), "8-10");
    assert.equal(recoverMonthDayRepRange(d, "m\\-d", "local"), "8-10");
    assert.equal(recoverMonthDayRepRange(d, "yyyy-mm-dd", "local"), null);
  });

  it("recovers 8-10 from a UTC August 10 Date (Excel serial path)", () => {
    const d = new Date(Date.UTC(2025, 7, 10));
    assert.equal(recoverMonthDayRepRange(d, "m-d", "utc"), "8-10");
  });

  it("does not use UTC fields on local Dates by default", () => {
    // Default calendar is local — UTC-midnight Aug 10 may be Aug 9 locally in US zones,
    // but local midnight Aug 10 always yields 8-10.
    const local = new Date(2025, 7, 10);
    assert.equal(recoverMonthDayRepRange(local, "m-d"), "8-10");
  });

  it("maps excel serials to UTC dates for recovery", () => {
    const d = excelSerialToUtcDate(45000);
    assert.ok(d instanceof Date);
    assert.equal(excelSerialToUtcDate(-1), null);
    assert.equal(excelSerialToUtcDate(Number.NaN), null);
  });
});
