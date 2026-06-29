#!/usr/bin/env node
/**
 * Live smoke test for generateProgramPipeline using a payload JSON file.
 * Uses the same pipeline code as the deployed generateProgram callable (minus auth/Firestore slot).
 *
 * Usage:
 *   OPENROUTER_API_KEY=... OPENROUTER_MODEL=deepseek/deepseek-v4-pro \
 *     node functions/scripts/smoke-generate-program.mjs /tmp/stratum-smoke/payload.json
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import OpenAI from "openai";
import {
  generateProgramWithDeps,
} from "../lib/program/generateProgram.js";
import {
  parseGenerationContext,
  toNormalizationRequest,
} from "../lib/program/programPayloadContext.js";
import { createOpenRouterClient, programGenerationSessionId } from "../lib/openrouterSession.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function loadLiftNames(payloadPath) {
  const payload = JSON.parse(fs.readFileSync(payloadPath, "utf8"));
  const names = new Map();
  for (const lift of [...(payload.provenLifts ?? []), ...(payload.availableLifts ?? [])]) {
    names.set(lift.id, lift.name);
  }
  return names;
}

function summarizeProgram(program, liftNames) {
  const workouts = program?.workouts ?? [];
  const lines = [];
  let alternatingCount = 0;
  let duplicateLiftIds = 0;
  for (const workout of workouts) {
    lines.push(`\n${workout.name ?? "Workout"} (${workout.weekday ?? "?"})`);
    const seen = new Set();
    for (const lift of workout.workoutLifts ?? []) {
      const name = liftNames.get(lift.liftId) ?? lift.liftId;
      if (/^(Alternating|Alternate)\b/i.test(name)) alternatingCount++;
      if (seen.has(lift.liftId)) duplicateLiftIds++;
      seen.add(lift.liftId);
      const sets = lift.workoutLiftSets?.length ?? 0;
      const first = lift.workoutLiftSets?.[0];
      const reps = first ? `${first.repMin}-${first.repMax}` : "?";
      lines.push(`  - ${name} (${lift.liftId}) — ${sets} x ${reps}`);
    }
  }
  return { lines, alternatingCount, duplicateLiftIds, workoutCount: workouts.length };
}

async function main() {
  const payloadPath = process.argv[2];
  if (!payloadPath) {
    console.error("Usage: node functions/scripts/smoke-generate-program.mjs <payload.json>");
    process.exit(1);
  }
  const apiKey = process.env.OPENROUTER_API_KEY;
  const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
  if (!apiKey) {
    console.error("OPENROUTER_API_KEY is required");
    process.exit(1);
  }

  const payload = fs.readFileSync(payloadPath, "utf8");
  const liftNames = loadLiftNames(payloadPath);
  const normalizationRequest = toNormalizationRequest(parseGenerationContext(payload));
  const sessionId = programGenerationSessionId("smoke-test-user");
  const client = createOpenRouterClient(apiKey, sessionId);

  console.log(`Running pipeline with model=${model} sessionId=${sessionId}`);
  const started = Date.now();
  const program = await generateProgramWithDeps(
    client,
    model,
    payload,
    normalizationRequest,
    sessionId,
    { skipFinalValidation: false },
  );
  const elapsedMs = Date.now() - started;
  const summary = summarizeProgram(program, liftNames);

  console.log(`\nCompleted in ${elapsedMs}ms`);
  console.log(`Workouts: ${summary.workoutCount}`);
  console.log(`Alternating-prefixed lifts: ${summary.alternatingCount}`);
  console.log(`Duplicate liftIds within workouts: ${summary.duplicateLiftIds}`);
  console.log(summary.lines.join("\n"));

  const outPath = path.join(path.dirname(payloadPath), "program-result.json");
  fs.writeFileSync(outPath, JSON.stringify(program, null, 2));
  console.log(`\nWrote ${outPath}`);

  if (summary.alternatingCount > 2) {
    console.error("\nFAIL: too many alternating-prefixed lifts");
    process.exit(2);
  }
  if (summary.duplicateLiftIds > 0) {
    console.error("\nFAIL: duplicate liftIds within a workout");
    process.exit(3);
  }
  if (summary.workoutCount === 0) {
    console.error("\nFAIL: empty program");
    process.exit(4);
  }
  console.log("\nPASS: smoke checks OK");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
