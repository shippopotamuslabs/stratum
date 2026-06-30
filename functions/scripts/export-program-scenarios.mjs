#!/usr/bin/env node
/**
 * Batch-generate programs from a JSON scenario file and write review CSVs.
 *
 * Usage:
 *   npm run export-scenarios
 *   npm run export-scenarios -- --config scripts/my-scenarios.json --output /tmp/programs.csv
 *
 * Outputs:
 *   programs.csv         — one row per lift (best for eyeballing exercise order & set counts)
 *   programs-summary.csv — one row per workout (volume totals, validation flags)
 *   programs.md          — readable program/workout/lift layout for review
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  exportProgramsToCsv,
  loadScenarioBatchFile,
} from "../lib/program/exportProgramScenarios.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const functionsRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(functionsRoot, "..");

function parseArgs(argv) {
  let configPath = path.join(functionsRoot, "scripts/scenarios.example.json");
  let outputPath = path.join(functionsRoot, "out/programs.csv");

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--config" && argv[i + 1]) {
      configPath = path.resolve(argv[++i]);
    } else if (arg === "--output" && argv[i + 1]) {
      outputPath = path.resolve(argv[++i]);
    } else if (arg === "--help" || arg === "-h") {
      console.log(`Usage: node scripts/export-program-scenarios.mjs [--config path] [--output path]

  --config   JSON scenario batch file (default: scripts/scenarios.example.json)
  --output   Detail CSV path (summary written alongside as *-summary.csv)
`);
      process.exit(0);
    }
  }

  return { configPath, outputPath };
}

async function main() {
  const { configPath, outputPath } = parseArgs(process.argv.slice(2));

  if (!fs.existsSync(configPath)) {
    console.error(`Config not found: ${configPath}`);
    process.exit(1);
  }

  const batch = loadScenarioBatchFile(configPath);
  if (!batch.scenarios?.length) {
    console.error("No scenarios defined in config.");
    process.exit(1);
  }

  console.log(`Generating ${batch.scenarios.length} scenario(s) from ${configPath} ...`);

  const result = await exportProgramsToCsv({
    repoRoot,
    batch,
    outputPath,
  });

  console.log(`Wrote ${result.detailRowCount} lift rows → ${result.outputPath}`);
  console.log(`Wrote ${result.summaryRowCount} workout rows → ${result.summaryPath}`);
  console.log(`Wrote markdown → ${result.markdownPath}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
