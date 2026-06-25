import type { Bucket } from "@google-cloud/storage";

const SAMPLE_PREVIEW_ROWS = 30;
const SLICE_MONTHS_MIN = 3;
const SLICE_MONTHS_MAX = 6;

export function limitSamplePreview(text: string): string {
  const lines = text.split("\n");
  if (lines.length <= SAMPLE_PREVIEW_ROWS) {
    return text;
  }
  return lines.slice(0, SAMPLE_PREVIEW_ROWS).join("\n");
}


export async function buildFullSlice(
  rawText: string,
  sliceStoragePath: string,
  bucket: Bucket,
): Promise<string> {
  const sliced = sliceExportByTimeWindow(rawText);
  await bucket.file(sliceStoragePath).save(sliced, { contentType: "text/plain" });
  return sliceStoragePath;
}

function sliceExportByTimeWindow(text: string): string {
  const trimmed = text.trim();
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    return sliceJsonExport(trimmed);
  }
  return sliceCsvExport(text);
}

function sliceCsvExport(text: string): string {
  const lines = text.split("\n").filter((line) => line.length > 0);
  if (lines.length <= 1) {
    return text;
  }
  const header = lines[0];
  const dateIdx = findDateColumnIndex(header);
  if (dateIdx < 0) {
    return text;
  }
  const datedRows: Array<{ line: string; ms: number }> = [];
  for (const line of lines.slice(1)) {
    const cols = splitCsvLine(line);
    const parsed = parseDate(cols[dateIdx] ?? "");
    if (parsed !== null) {
      datedRows.push({ line, ms: parsed });
    }
  }
  if (datedRows.length === 0) {
    return text;
  }
  const now = Date.now();
  const windowStart = now - SLICE_MONTHS_MAX * 30 * 24 * 60 * 60 * 1000;
  const windowEnd = now - SLICE_MONTHS_MIN * 30 * 24 * 60 * 60 * 1000;
  const inWindow = datedRows.filter((row) => row.ms >= windowStart && row.ms <= now);
  const selected = inWindow.length > 0 ? inWindow : datedRows.slice(-500);
  return [header, ...selected.map((r) => r.line)].join("\n");
}

function sliceJsonExport(text: string): string {
  try {
    const parsed = JSON.parse(text) as unknown;
    if (Array.isArray(parsed)) {
      return JSON.stringify(parsed.slice(-500));
    }
    if (parsed && typeof parsed === "object") {
      const obj = parsed as Record<string, unknown>;
      for (const key of ["workouts", "sessions", "data", "items"]) {
        const arr = obj[key];
        if (Array.isArray(arr)) {
          return JSON.stringify({ ...obj, [key]: arr.slice(-500) });
        }
      }
    }
  } catch {
    // fall through
  }
  return text;
}

function findDateColumnIndex(header: string): number {
  const cols = splitCsvLine(header.toLowerCase());
  const candidates = ["date", "timestamp", "started_at", "start_time", "completed_at", "time"];
  for (const name of candidates) {
    const idx = cols.indexOf(name);
    if (idx >= 0) {
      return idx;
    }
  }
  return -1;
}

function splitCsvLine(line: string): string[] {
  return line.split(",");
}

function parseDate(value: string): number | null {
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}
