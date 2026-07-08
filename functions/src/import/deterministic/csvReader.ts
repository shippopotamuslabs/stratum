/**
 * Minimal RFC-4180-ish CSV reader.
 *
 * The export formats Stratum parses deterministically are simple: quoted fields
 * with doubled-quote escaping, no embedded newlines inside quotes. This handles
 * both the Hevy comma-delimited variant and the Strong / Alpha Progression
 * semicolon-delimited variant. Empty trailing fields are preserved so header
 * indexes stay stable regardless of how many delimiters a row ends with.
 *
 * Kept dependency-free on purpose — the functions/ package stays lean and the
 * parsing rules here are narrow enough that a library would not pay for itself.
 */

/**
 * Parse a single CSV line into fields, honouring double-quoted fields with
 * `""` escaping. The delimiter is configurable so the same logic handles
 * Hevy's `,` and Strong's `;`.
 */
export function parseCsvLine(line: string, delimiter: string): string[] {
  const fields: string[] = [];
  let field = "";
  let inQuotes = false;
  let i = 0;
  while (i < line.length) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
        i += 1;
        continue;
      }
      field += ch;
      i += 1;
      continue;
    }
    if (ch === '"') {
      inQuotes = true;
      i += 1;
      continue;
    }
    if (ch === delimiter) {
      fields.push(field);
      field = "";
      i += 1;
      continue;
    }
    field += ch;
    i += 1;
  }
  fields.push(field);
  return fields;
}

/**
 * Parse a CSV document into a row matrix. `\r\n` and `\r` are normalised to
 * `\n` first. Blank trailing lines are dropped so a trailing newline in an
 * export does not produce a phantom empty row.
 */
export function parseCsv(text: string, delimiter: string): string[][] {
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n");
  const rows: string[][] = [];
  for (const line of lines) {
    if (line.length === 0) {
      continue;
    }
    rows.push(parseCsvLine(line, delimiter));
  }
  return rows;
}

/**
 * Build a column-name -> index map from a header row. Trims whitespace from
 * header names so a stray space never breaks a lookup.
 */
export function headerIndex(header: string[]): Record<string, number> {
  const map: Record<string, number> = {};
  header.forEach((name, index) => {
    map[name.trim()] = index;
  });
  return map;
}

/** Read a field by header name, returning "" when the column or value is absent. */
export function field(
  row: string[],
  headers: Record<string, number>,
  name: string,
): string {
  const index = headers[name];
  if (index === undefined || index >= row.length) {
    return "";
  }
  return row[index] ?? "";
}
