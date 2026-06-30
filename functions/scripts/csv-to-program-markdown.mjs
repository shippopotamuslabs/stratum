#!/usr/bin/env node
/**
 * Convert a programs detail CSV to readable markdown.
 *
 * Usage:
 *   npm run programs-to-markdown
 *   npm run programs-to-markdown -- --input out/sean-review/programs.csv --output out/sean-review/programs.md
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { detailCsvToMarkdown } from "../lib/program/exportProgramScenarios.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const functionsRoot = path.resolve(__dirname, "..");

function parseArgs(argv) {
  let inputPath = path.join(functionsRoot, "out/sean-review/programs.csv");
  let outputPath = inputPath.replace(/\.csv$/i, ".md");

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--input" && argv[i + 1]) {
      inputPath = path.resolve(argv[++i]);
      outputPath = inputPath.replace(/\.csv$/i, ".md");
    } else if (arg === "--output" && argv[i + 1]) {
      outputPath = path.resolve(argv[++i]);
    } else if (arg === "--help" || arg === "-h") {
      console.log(`Usage: node scripts/csv-to-program-markdown.mjs [--input detail.csv] [--output programs.md]`);
      process.exit(0);
    }
  }

  return { inputPath, outputPath };
}

function main() {
  const { inputPath, outputPath } = parseArgs(process.argv.slice(2));
  if (!fs.existsSync(inputPath)) {
    console.error(`Input not found: ${inputPath}`);
    process.exit(1);
  }

  const markdown = detailCsvToMarkdown(fs.readFileSync(inputPath, "utf8"));
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, markdown, "utf8");
  console.log(`Wrote ${outputPath}`);
}

main();
