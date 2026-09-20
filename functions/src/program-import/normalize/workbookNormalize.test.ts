import { describe, it } from "node:test";
import assert from "node:assert/strict";
import ExcelJS from "exceljs-community";
import { xlsxFromAoa } from "../../testUtils/xlsxFixtures";
import { cellToDisplay, normalizeWorkbook, renderSheetView } from "./workbookNormalize";

describe("workbookNormalize", () => {
  it("parses a tidy CSV program table", async () => {
    const csv = [
      "Day,Lift Name,Sets,Rep Range",
      "Upper A,Incline DB Bench Press,4,8-10",
      ",High Row,3,8-10",
      "Lower A,Hack Squat,2,8-10",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    assert.equal(wb.kind, "csv");
    assert.equal(wb.unreadable, false);
    assert.equal(wb.sheets.length, 1);
    assert.equal(wb.sheets[0]!.rows.length, 4);
    assert.ok(wb.sheets[0]!.rows[1]!.includes("Incline DB Bench Press"));
  });

  it("parses multi-sheet xlsx and drops empty rows", async () => {
    const bytes = await xlsxFromAoa({
      Program: [
        ["Day", "Lift", "Sets", "Reps"],
        ["Upper A", "Bench Press", 4, "8-10"],
        [null, null, null, null],
        ["Lower A", "Squat", 3, "5"],
      ],
      VolumeLookup: [
        ["Type", "Chest"],
        ["Press", 1],
      ],
    });
    const wb = await normalizeWorkbook(bytes);
    assert.equal(wb.kind, "xlsx");
    assert.equal(wb.sheets.length, 2);
    const program = wb.sheets.find((s) => s.name === "Program")!;
    assert.equal(program.rows.length, 3); // empty row dropped
  });

  it("recovers month-day dates on cells when format is m-d", async () => {
    // Numeric serial with an m-d number format: exceljs rehydrates it as a
    // UTC-midnight Date on read, and cellToDisplay recovers the month-day.
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("P");
    sheet.getCell("A1").value = "Reps";
    const dateCell = sheet.getCell("A2");
    dateCell.value = 45879; // 2025-08-10 Excel serial
    dateCell.numFmt = "m-d";
    const bytes = Buffer.from(await workbook.xlsx.writeBuffer());

    const wb = await normalizeWorkbook(bytes);
    const rows = wb.sheets[0]!.rows;
    assert.ok(rows.some((r) => r.includes("8-10")), `expected 8-10 in ${JSON.stringify(rows)}`);
    assert.equal(wb.sheets[0]!.recoveredDateCells, 1);
  });

  it("marks unreadable binary non-xlsx as unknown when null bytes present", async () => {
    const wb = await normalizeWorkbook(Buffer.from([0x00, 0x01, 0x02, 0xff]));
    // May be csv-decoded with replacement chars or unknown — null byte triggers unreadable
    assert.equal(wb.unreadable, true);
  });

  it("cellToDisplay recovers Date with m-d format using UTC calendar", () => {
    // exceljs surfaces date-formatted serials as UTC-midnight Dates; the
    // calendar fields are extracted in UTC so the day cannot shift.
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("P");
    const cell = sheet.getCell("A1");
    cell.value = new Date(Date.UTC(2025, 9, 12)); // October 12
    cell.numFmt = "m-d";
    const result = cellToDisplay(cell);
    assert.equal(result.text, "10-12");
    assert.equal(result.recoveredDate, true);
  });

  it("cellToDisplay blanks merged-follower cells", () => {
    // exceljs repeats the merge master's value on follower cells; the
    // normalizer must blank them (SheetJS surfaced them as missing).
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("P");
    sheet.getCell("A1").value = "Upper A";
    sheet.mergeCells("A1:A2");
    const follower = sheet.getCell("A2");
    assert.equal(follower.type, ExcelJS.ValueType.Merge);
    const result = cellToDisplay(follower);
    assert.equal(result.text, "");
    assert.equal(result.literal, false);
  });

  it("renderSheetView includes row numbers and region label", async () => {
    const wb = await normalizeWorkbook(
      Buffer.from("Day,Lift,Sets\nA,Squat,3\n", "utf8"),
    );
    const sheet = wb.sheets[0]!;
    const text = renderSheetView(sheet, [0, 1], { regionLabel: "Week 1" });
    assert.ok(text.includes('Sheet "Sheet1"'));
    assert.ok(text.includes("Week 1"));
    assert.ok(text.includes("Squat"));
  });

  it("normalizes an empty sheet to an empty matrix instead of throwing", async () => {
    // exceljs reports an all-zero dimensions model for an empty worksheet;
    // usedRangeBounds must return null so the reader never calls getCell(0, 0).
    const workbook = new ExcelJS.Workbook();
    workbook.addWorksheet("empty");
    const bytes = Buffer.from(await workbook.xlsx.writeBuffer());

    const wb = await normalizeWorkbook(bytes);
    assert.equal(wb.sheets.length, 1);
    assert.equal(wb.sheets[0]!.name, "empty");
    assert.deepEqual(wb.sheets[0]!.rows, []);
  });
});
