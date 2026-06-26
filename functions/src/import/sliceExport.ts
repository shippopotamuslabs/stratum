import type { Bucket } from "@google-cloud/storage";

const SAMPLE_PREVIEW_ROWS = 30;

export function limitSamplePreview(text: string): string {
  const lines = text.split("\n");
  if (lines.length <= SAMPLE_PREVIEW_ROWS) {
    return text;
  }
  return lines.slice(0, SAMPLE_PREVIEW_ROWS).join("\n");
}

/** Persists the full export for audit; conversion runs against the raw upload path. */
export async function buildFullSlice(
  rawText: string,
  sliceStoragePath: string,
  bucket: Bucket,
): Promise<string> {
  await bucket.file(sliceStoragePath).save(rawText, { contentType: "text/plain" });
  return sliceStoragePath;
}
