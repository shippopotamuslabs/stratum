/**
 * Test-only XLSX fixture builders backed by exceljs-community.
 *
 * Replaces the old SheetJS `aoa_to_sheet` / `write` fixture builders. The
 * parsers consume raw bytes, so fixtures go through a real write → read
 * round-trip, exercising the same decode path as production.
 */
import ExcelJS from "exceljs-community";

export type AoaCell = string | number | boolean | null | undefined;
export type AoaRow = AoaCell[];

/** Build a real .xlsx buffer from a sheet-name → row-matrix map. */
export async function xlsxFromAoa(sheets: Record<string, AoaRow[]>): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  for (const [name, aoa] of Object.entries(sheets)) {
    const worksheet = workbook.addWorksheet(name);
    for (const row of aoa) {
      worksheet.addRow(row.map((value) => (value === undefined ? null : value)));
    }
  }
  return Buffer.from(await workbook.xlsx.writeBuffer());
}
