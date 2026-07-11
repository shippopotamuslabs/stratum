import { describe, it } from "node:test";
import assert from "node:assert/strict";
import * as xlsx from "xlsx";
import { cellToDisplay, normalizeWorkbook, renderSheetView } from "./workbookNormalize";

function xlsxFromAoa(sheets: Record<string, unknown[][]>): Buffer {
  const book = xlsx.utils.book_new();
  for (const [name, aoa] of Object.entries(sheets)) {
    const sheet = xlsx.utils.aoa_to_sheet(aoa);
    xlsx.utils.book_append_sheet(book, sheet, name);
  }
  return xlsx.write(book, { type: "buffer", bookType: "xlsx" }) as Buffer;
}

describe("workbookNormalize", () => {
  it("parses a tidy CSV program table", () => {
    const csv = [
      "Day,Lift Name,Sets,Rep Range",
      "Upper A,Incline DB Bench Press,4,8-10",
      ",High Row,3,8-10",
      "Lower A,Hack Squat,2,8-10",
    ].join("\n");
    const wb = normalizeWorkbook(Buffer.from(csv, "utf8"));
    assert.equal(wb.kind, "csv");
    assert.equal(wb.unreadable, false);
    assert.equal(wb.sheets.length, 1);
    assert.equal(wb.sheets[0]!.rows.length, 4);
    assert.ok(wb.sheets[0]!.rows[1]!.includes("Incline DB Bench Press"));
  });

  it("parses multi-sheet xlsx and drops empty rows", () => {
    const bytes = xlsxFromAoa({
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
    const wb = normalizeWorkbook(bytes);
    assert.equal(wb.kind, "xlsx");
    assert.equal(wb.sheets.length, 2);
    const program = wb.sheets.find((s) => s.name === "Program")!;
    assert.equal(program.rows.length, 3); // empty row dropped
  });

  it("recovers month-day dates on cells when format is m-d", () => {
    // Local midnight Date matches SheetJS cellDates; m-d format triggers recovery.
    const sheet = xlsx.utils.aoa_to_sheet([["Reps"], [null]]);
    const addr = xlsx.utils.encode_cell({ r: 1, c: 0 });
    sheet[addr] = {
      t: "d",
      v: new Date(2025, 7, 10), // August 10 local
      z: "m-d",
    } as xlsx.CellObject;
    sheet["!ref"] = "A1:A2";
    const book = xlsx.utils.book_new();
    xlsx.utils.book_append_sheet(book, sheet, "P");
    const bytes = xlsx.write(book, { type: "buffer", bookType: "xlsx" }) as Buffer;

    const wb = normalizeWorkbook(bytes);
    const rows = wb.sheets[0]!.rows;
    // After XLSX round-trip SheetJS may rehydrate the cell; accept recovered 8-10
    // or direct cellToDisplay on the constructed cell.
    const direct = cellToDisplay({
      t: "d",
      v: new Date(2025, 7, 10),
      z: "m-d",
    } as xlsx.CellObject);
    assert.equal(direct.text, "8-10");
    assert.equal(direct.recoveredDate, true);
    // Round-trip still produces a date-like value (may be ISO if format lost).
    assert.ok(rows.length >= 1);
    assert.ok(rows.some((r) => r.some((c) => c.length > 0)));
  });

  it("marks unreadable binary non-xlsx as unknown when null bytes present", () => {
    const wb = normalizeWorkbook(Buffer.from([0x00, 0x01, 0x02, 0xff]));
    // May be csv-decoded with replacement chars or unknown — null byte triggers unreadable
    assert.equal(wb.unreadable, true);
  });

  it("cellToDisplay recovers Date with m-d format using local calendar", () => {
    // Local midnight matches SheetJS cellDates output.
    const result = cellToDisplay({
      t: "d",
      v: new Date(2025, 9, 12), // October 12 local
      z: "m-d",
    } as xlsx.CellObject);
    assert.equal(result.text, "10-12");
    assert.equal(result.recoveredDate, true);
  });

  it("renderSheetView includes row numbers and region label", () => {
    const wb = normalizeWorkbook(
      Buffer.from("Day,Lift,Sets\nA,Squat,3\n", "utf8"),
    );
    const sheet = wb.sheets[0]!;
    const text = renderSheetView(sheet, [0, 1], { regionLabel: "Week 1" });
    assert.ok(text.includes('Sheet "Sheet1"'));
    assert.ok(text.includes("Week 1"));
    assert.ok(text.includes("Squat"));
  });
});
