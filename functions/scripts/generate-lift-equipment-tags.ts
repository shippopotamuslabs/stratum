/**
 * One-time offline generator for the bundled `lift-equipment-tags.json` asset.
 *
 * Maps every seed lift (id + name + movementPattern) to one or more [GymEquipment] enum values:
 * - A deterministic keyword pre-pass handles the obvious ~60% (cable/machine/smith/leg press/etc.
 *   in the name) so Flash only classifies the ambiguous remainder, keeping cost down.
 * - The remainder is batched through OpenRouter `OPENROUTER_MODEL_SMALL` with a strict JSON schema
 *   enum-bound to the Kotlin `GymEquipment` enum names.
 *
 * Run locally (not a deployed callable) — per the import enablement plan the asset is built once
 * and committed so every user ships identical equipment tags without a per-user Flash pass.
 *
 * Usage:
 *   OPENROUTER_API_KEY=... npm --prefix functions run build
 *   node functions/scripts/generate-lift-equipment-tags.js \
 *     --seed ../../core/data/src/main/assets/lifts/seed-lifts.json \
 *     --out   ../../core/data/src/main/assets/lifts/lift-equipment-tags.json
 *
 * If OPENROUTER_API_KEY is absent the script writes the keyword-prepass result only (deterministic
 * tags), which is enough to keep the asset non-empty while a maintainer runs the Flash pass.
 */
import OpenAI from "openai";
import { readFileSync, writeFileSync } from "node:fs";
import { buildOpenRouterChatCompletionExtras, createOpenRouterClient } from "../src/openrouterSession";
import {
  buildOpenRouterProviderRouting,
} from "../src/openrouterRouting";

const GYM_EQUIPMENT = [
  "Barbell", "FixedBarbell", "Dumbbell", "EzBar", "Kettlebell", "Band", "Bodyweight",
  "FlatBench", "AdjustableBench", "PowerRack",
  "SmithMachine", "CableStation", "CableCrossover", "LatPulldownMachine",
  "SeatedCableRowStation", "TBarRowStation", "LegPress", "HackSquat",
  "LegExtension", "LegCurl", "CalfRaiseMachine", "AbCrunchMachine", "PecDeck",
  "PreacherCurlMachine", "HipThrustMachine", "Ghd", "LegRaiseCaptainChair",
] as const;

interface SeedLift {
  id: string;
  name: string;
  movementPattern?: string;
  equipment?: string | null;
}

// Deterministic keyword -> equipment. Conservative: only fires when the token is unambiguous.
const KEYWORD_RULES: Array<{ re: RegExp; equipment: string[] }> = [
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
  // T-bar before generic "bar" so plate-loaded T stations are not tagged Cable/Barbell only.
  { re: /\bt-?bar\b/i, equipment: ["TBarRowStation"] },
  { re: /\blandmine\b/i, equipment: ["Barbell"] },
  // Upright rows on a free-weight bar are usually fixed preloaded bars (+10), not plate sleeves.
  // Bent-over barbell rows stay Barbell → double plates via the generic barbell rule.
  { re: /\bupright\b.*\b(?:barbell|bar)\b.*\brow|\b(?:barbell|bar)\b.*\bupright\b.*\brow/i, equipment: ["FixedBarbell"] },
  { re: /\bseated cable row\b/i, equipment: ["CableStation", "SeatedCableRowStation"] },
  { re: /\bdumbbell\b/i, equipment: ["Dumbbell"] },
  { re: /\bfixed[- ]?(?:weight[- ]?)?bar(?:bell)?\b/i, equipment: ["FixedBarbell"] },
  { re: /\b(?:barbell|bar)\b/i, equipment: ["Barbell"] },
  { re: /\bez[- ]?bar|ez curl\b/i, equipment: ["EzBar"] },
  { re: /\bkettlebell\b/i, equipment: ["Kettlebell"] },
  { re: /\bresistance band|\bband\b/i, equipment: ["Band"] },
  { re: /\bbodyweight|\bw-eigh?a?ge?d?\b/i, equipment: ["Bodyweight"] },
];

const EQUIPMENT_SET = new Set<string>(GYM_EQUIPMENT);

function keywordPass(name: string): string[] | null {
  const matched = new Set<string>();
  for (const rule of KEYWORD_RULES) {
    if (rule.re.test(name)) {
      for (const e of rule.equipment) matched.add(e);
    }
  }
  return matched.size > 0 ? [...matched] : null;
}

function loadSeed(path: string): SeedLift[] {
  const raw = JSON.parse(readFileSync(path, "utf8"));
  if (!Array.isArray(raw)) {
    throw new Error(`Seed file ${path} is not an array`);
  }
  return raw as SeedLift[];
}

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
              equipment: {
                type: "array",
                items: { type: "string", enum: [...GYM_EQUIPMENT] },
              },
            },
            required: ["id", "equipment"],
          },
        },
      },
      required: ["lifts"],
    },
  };
}

const SYSTEM_PROMPT = `You tag strength-training exercise names with the equipment required to perform them.
For each lift id + name, return one or more equipment enum values. Use the minimal set that the lifter must have available to do THIS exercise (not variants). A barbell bench press needs Barbell + FlatBench + PowerRack; a dumbbell bench press needs Dumbbell + FlatBench; a cable row needs CableStation + SeatedCableRowStation. If a lift is bodyweight-only, use Bodyweight. Only return equipment from the enum. Return an empty array for a lift that uses generic free weights with no fixed station requirement beyond a barbell/dumbbell only if no other equipment applies.`;

async function flashClassify(lifts: SeedLift[], apiKey: string, model: string): Promise<Record<string, string[]>> {
  const sessionId = "stratum-lift-equipment-tagging";
  const client = createOpenRouterClient(apiKey, sessionId);
  const result: Record<string, string[]> = {};
  const BATCH = 75;
  for (let i = 0; i < lifts.length; i += BATCH) {
    const slice = lifts.slice(i, i + BATCH);
    const userPrompt = JSON.stringify(
      {
        lifts: slice.map((l) => ({ id: l.id, name: l.name, movementPattern: l.movementPattern ?? null })),
      },
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
    } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
    const content = completion.choices[0]?.message?.content;
    if (!content) continue;
    let parsed: any;
    try {
      parsed = JSON.parse(content);
    } catch {
      continue;
    }
    for (const row of parsed?.lifts ?? []) {
      if (!row || typeof row.id !== "string") continue;
      const equip = Array.isArray(row.equipment)
        ? row.equipment.filter((e: unknown) => typeof e === "string" && EQUIPMENT_SET.has(e as string))
        : [];
      if (equip.length > 0) result[row.id] = equip;
    }
  }
  return result;
}

async function main() {
  const args = Object.fromEntries(
    process.argv.slice(2).map((a) => {
      const idx = a.indexOf("=");
      if (idx > 0) return [a.slice(2, idx), a.slice(idx + 1)];
      const nextIdx = process.argv.indexOf(a) + 1;
      return [a.slice(2), process.argv[nextIdx] ?? ""];
    }),
  );
  const seedPath = args.seed ?? "../core/data/src/main/assets/lifts/seed-lifts.json";
  const outPath = args.out ?? "../core/data/src/main/assets/lifts/lift-equipment-tags.json";
  const seed = loadSeed(seedPath);
  const tagged: Array<{ id: string; equipment: string[] }> = [];
  const needsFlash: SeedLift[] = [];
  for (const lift of seed) {
    const kw = keywordPass(lift.name);
    if (kw && kw.length > 0) {
      tagged.push({ id: lift.id, equipment: kw });
    } else {
      needsFlash.push(lift);
    }
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

  // Drop entries with empty equipment (no signal = no closure contribution).
  const filtered = tagged.filter((t) => t.equipment.length > 0).sort((a, b) => a.id.localeCompare(b.id));
  writeFileSync(outPath, JSON.stringify(filtered, null, 2) + "\n", "utf8");
  console.error(`wrote ${filtered.length} entries to ${outPath}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
