/**
 * One-time offline generator (JS, runs against compiled `lib/`) for the bundled
 * `lift-equipment-tags.json` asset. See generate-lift-equipment-tags.ts for the full rationale.
 *
 * Maps every seed lift to one or more GymEquipment enum values:
 * - deterministic keyword pre-pass handles the obvious ~60% (cable/machine/smith/leg press/etc.)
 * - the ambiguous remainder is batched through OpenRouter OPENROUTER_MODEL_SMALL when
 *   OPENROUTER_API_KEY is set; otherwise only the keyword-prepass result is written.
 *
 * Usage:
 *   node functions/scripts/generate-lift-equipment-tags.js \
 *     --seed=../../core/data/src/main/assets/lifts/seed-lifts.json \
 *     --out=../../core/data/src/main/assets/lifts/lift-equipment-tags.json
 */
"use strict";
const fs = require("node:fs");
const OpenAI = require("openai");
const {
  createOpenRouterClient,
  buildOpenRouterChatCompletionExtras,
} = require("../lib/openrouterSession");
const {
  buildOpenRouterProviderRouting,
} = require("../lib/openrouterRouting");

const GYM_EQUIPMENT = [
  "Barbell", "Dumbbell", "EzBar", "Kettlebell", "Band", "Bodyweight",
  "FlatBench", "AdjustableBench", "PowerRack",
  "SmithMachine", "CableStation", "CableCrossover", "LatPulldownMachine",
  "SeatedCableRowStation", "TBarRowStation", "LegPress", "HackSquat",
  "LegExtension", "LegCurl", "CalfRaiseMachine", "AbCrunchMachine", "PecDeck",
  "PreacherCurlMachine", "HipThrustMachine", "Ghd", "LegRaiseCaptainChair",
];
const EQUIPMENT_SET = new Set(GYM_EQUIPMENT);

const KEYWORD_RULES = [
  { re: /\bsmith\b/i, equipment: ["SmithMachine"] },
  { re: /\bcable\b/i, equipment: ["CableStation"] },
  { re: /\b(?:leg ?press|leg-press)\b/i, equipment: ["LegPress"] },
  { re: /\bhack ?squat\b/i, equipment: ["HackSquat"] },
  { re: /\bleg extension\b/i, equipment: ["LegExtension"] },
  { re: /\bleg curl\b/i, equipment: ["LegCurl"] },
  { re: /\bcalf (?:raise|press)\b/i, equipment: ["CalfRaiseMachine"] },
  { re: /\b(?:lat ?pulldown|pulldown)\b/i, equipment: ["CableStation", "LatPulldownMachine"] },
  { re: /\b(?:cable ?crossover|crossover)\b/i, equipment: ["CableStation", "CableCrossover"] },
  { re: /\b(?:pec deck|butterfly|flyes?)\b/i, equipment: ["PecDeck"] },
  { re: /\bpreacher\b/i, equipment: ["PreacherCurlMachine"] },
  { re: /\bhip thrust\b/i, equipment: ["Barbell", "HipThrustMachine"] },
  { re: /\bglute ham raise|\bghr\b|\bghd\b/i, equipment: ["Ghd"] },
  { re: /\b captain'?s chair|leg raise\b/i, equipment: ["LegRaiseCaptainChair"] },
  { re: /\bab crunch\b/i, equipment: ["AbCrunchMachine"] },
  { re: /\bt-?bar row\b/i, equipment: ["TBarRowStation"] },
  { re: /\bseated cable row\b/i, equipment: ["CableStation", "SeatedCableRowStation"] },
  { re: /\bdumbbell\b/i, equipment: ["Dumbbell"] },
  { re: /\b(?:barbell|bar)\b/i, equipment: ["Barbell"] },
  { re: /\bez[- ]?bar|ez curl\b/i, equipment: ["EzBar"] },
  { re: /\bkettlebell\b/i, equipment: ["Kettlebell"] },
  { re: /\bresistance band|\bband\b/i, equipment: ["Band"] },
  { re: /\bbodyweight\b/i, equipment: ["Bodyweight"] },
];

function keywordPass(name) {
  const matched = new Set();
  for (const rule of KEYWORD_RULES) {
    if (rule.re.test(name)) {
      for (const e of rule.equipment) matched.add(e);
    }
  }
  return matched.size > 0 ? [...matched] : null;
}

function loadSeed(path) {
  const raw = JSON.parse(fs.readFileSync(path, "utf8"));
  if (!Array.isArray(raw)) throw new Error(`Seed file ${path} is not an array`);
  return raw;
}

const SYSTEM_PROMPT = `You tag strength-training exercise names with the equipment required to perform them.
For each lift id + name, return one or more equipment enum values. Use the minimal set that the lifter must have available to do THIS exercise (not variants). A barbell bench press needs Barbell + FlatBench + PowerRack; a dumbbell bench press needs Dumbbell + FlatBench; a cable row needs CableStation + SeatedCableRowStation. If a lift is bodyweight-only, use Bodyweight. Only return equipment from the enum.`;

function jsonSchema() {
  return {
    name: "lift_equipment_tags",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        lifts: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              id: { type: "string" },
              equipment: { type: "array", items: { type: "string", enum: [...GYM_EQUIPMENT] } },
            },
            required: ["id", "equipment"],
          },
        },
      },
      required: ["lifts"],
    },
  };
}

async function flashClassify(lifts, apiKey, model) {
  const sessionId = "stratum-lift-equipment-tagging";
  const client = createOpenRouterClient(apiKey, sessionId);
  const result = {};
  const BATCH = 75;
  for (let i = 0; i < lifts.length; i += BATCH) {
    const slice = lifts.slice(i, i + BATCH);
    const userPrompt = JSON.stringify(
      { lifts: slice.map((l) => ({ id: l.id, name: l.name, movementPattern: l.movementPattern ?? null })) },
      null,
      2,
    );
    const completion = await client.chat.completions.create({
      model,
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        { role: "user", content: userPrompt },
      ],
      response_format: { type: "json_schema", json_schema: jsonSchema() },
      ...buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }),
    });
    const content = completion.choices[0]?.message?.content;
    if (!content) continue;
    let parsed;
    try { parsed = JSON.parse(content); } catch { continue; }
    for (const row of parsed?.lifts ?? []) {
      if (!row || typeof row.id !== "string") continue;
      const equip = Array.isArray(row.equipment)
        ? row.equipment.filter((e) => typeof e === "string" && EQUIPMENT_SET.has(e))
        : [];
      if (equip.length > 0) result[row.id] = equip;
    }
  }
  return result;
}

async function main() {
  const args = {};
  process.argv.slice(2).forEach((a) => {
    const m = a.match(/^--([^=]+)=(.*)$/);
    if (m) args[m[1]] = m[2];
  });
  const seedPath = args.seed ?? "../core/data/src/main/assets/lifts/seed-lifts.json";
  const outPath = args.out ?? "../core/data/src/main/assets/lifts/lift-equipment-tags.json";
  const seed = loadSeed(seedPath);
  const tagged = [];
  const needsFlash = [];
  for (const lift of seed) {
    const kw = keywordPass(lift.name);
    if (kw && kw.length > 0) tagged.push({ id: lift.id, equipment: kw });
    else needsFlash.push(lift);
  }
  console.error(`keyword pass tagged ${tagged.length}/${seed.length}; ${needsFlash.length} need Flash`);

  const apiKey = process.env.OPENROUTER_API_KEY;
  const model = process.env.OPENROUTER_MODEL_SMALL ?? "deepseek/deepseek-v4-flash";
  if (apiKey && needsFlash.length > 0) {
    const flashResult = await flashClassify(needsFlash, apiKey, model);
    for (const lift of needsFlash) {
      const equip = flashResult[lift.id];
      if (equip && equip.length > 0) tagged.push({ id: lift.id, equipment: equip });
    }
    console.error(`flash tagged ${Object.keys(flashResult).length} ambiguous lifts`);
  } else if (!apiKey) {
    console.error("OPENROUTER_API_KEY missing — writing keyword-prepass-only asset.");
  }

  const filtered = tagged.filter((t) => t.equipment.length > 0).sort((a, b) => a.id.localeCompare(b.id));
  fs.writeFileSync(outPath, JSON.stringify(filtered, null, 2) + "\n", "utf8");
  console.error(`wrote ${filtered.length} entries to ${outPath}`);
}

main().catch((err) => { console.error(err); process.exit(1); });
