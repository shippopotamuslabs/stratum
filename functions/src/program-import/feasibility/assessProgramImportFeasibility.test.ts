import { describe, it } from "node:test";
import assert from "node:assert/strict";
import ExcelJS from "exceljs-community";
import { normalizeWorkbook } from "../normalize/workbookNormalize";
import { assessProgramImportFeasibility } from "./assessProgramImportFeasibility";

describe("assessProgramImportFeasibility", () => {
  it("accepts a tidy personal program CSV", async () => {
    const csv = [
      "Day,Movement Pattern,Lift Name,Sets,Rep Range",
      "Upper A (Sunday),Horizontal Press,Incline DB Bench Press,4,8-10",
      ",Vertical Pull,High Row (Nautilus),3,8-10",
      ",Chest Isolation,Chest Press (Nautilus),4,10-12",
      "Lower A (Monday),Leg Push,Hack Squat,2,8-10",
      ",Hip Hinge,Deadlift (Nautilus),4,8-10",
      ",Quad Isolation,Leg Extensions,3,10-12",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept");
    assert.equal(result.reasonCode, "ok");
    assert.equal(result.bestSheetIndex, 0);
  });

  it("accepts a Nippard-style multi-week coaching table", async () => {
    const csv = [
      "Section,Exercise,Warm-up Sets,Working Sets,Reps,RPE,Rest",
      "Week 1,Exercise,,,,,",
      "Full Body 1: Squat OHP,Back squat,4,1,5,7.5,3-4 min",
      ",Back squat,0,2,8,N/A,3-4 min",
      ",Overhead press,2,3,8,N/A,2-3 min",
      ",Helms row,1,3,12-15,9,1-2 min",
      "Full Body 2,Deadlift,4,3,4,N/A,3-5 min",
      ",Barbell bench press,4,1,3,8.5,4-5 min",
      "Week 2,Exercise,,,,,",
      "Lower #1,Deadlift,4,3,3,N/A,3-5 min",
      ",Leg curl,1,3,6-8,8,1-2 min",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept", result.message);
  });

  it("rejects SBS-like calculator multi-sheet workbooks", async () => {
    // Build sheets with cross-sheet formulas + frequency tabs (Quick Setup / Setup / 2x–4x).
    const book = new ExcelJS.Workbook();
    const qs = book.addWorksheet("Quick Setup");
    qs.addRow(["Main Lifts", "Maxes"]);
    qs.addRow(["Squat", 490]);
    const setup = book.addWorksheet("Setup");
    setup.addRow(["Lift"]);
    setup.getCell("A2").value = { formula: "'Quick Setup'!C5", result: 1 };
    setup.getCell("B2").value = { formula: "'Quick Setup'!C60", result: 2 };
    setup.getCell("C2").value = { formula: "Setup!B3", result: 3 };
    for (const name of ["2x", "3x", "4x"]) {
      const sh = book.addWorksheet(name);
      sh.addRow(["Day 1"]);
      sh.getCell("A2").value = { formula: "Setup!A3", result: 1 };
      sh.getCell("B2").value = { formula: "Setup!C3", result: 2 };
    }
    const calcBytes = Buffer.from(await book.xlsx.writeBuffer());
    const wb = await normalizeWorkbook(calcBytes);
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "calculator");
  });

  it("rejects garbage CSV", async () => {
    const wb = await normalizeWorkbook(Buffer.from("foo,bar\n1,2\n3,4\n", "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "reject");
    assert.ok(["not_a_program", "too_thin"].includes(result.reasonCode));
  });

  it("accepts a Spanish-language program table", async () => {
    const csv = [
      "Día,Ejercicio,Series,Repeticiones",
      "Superior A,Press de banca,4,8-10",
      ",Remo con barra,3,8-10",
      ",Curl de bíceps,3,10-12",
      "Inferior A,Sentadilla,3,5",
      ",Peso muerto rumano,3,8",
      ",Extensión de cuádriceps,3,10-12",
    ].join("\n");
    const wb = await normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept", result.message);
  });

  it("rejects empty workbook", () => {
    const result = assessProgramImportFeasibility({
      kind: "xlsx",
      sheets: [],
      unreadable: false,
    });
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "empty");
  });
});
