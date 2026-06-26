import { GYM_EQUIPMENT, type GymEquipment } from "./gymEquipmentVocabulary";

/** Equipment enum recovery uses a slightly lower bar than lift-name matching (smaller closed set). */
export const EQUIPMENT_ENUM_MATCH_THRESHOLD = 0.5;

const LEVENSHTEIN_DISTANCE_THRESHOLD = 3;
const LEVENSHTEIN_BONUS = 0.5;

/** Shared suffix tokens — overlap on these alone should not beat a discriminative token match. */
const GENERIC_EQUIPMENT_TOKENS = new Set([
  "machine",
  "station",
  "bench",
  "raise",
  "curl",
  "press",
  "bar",
  "rack",
]);

const ENUM_CATALOG = GYM_EQUIPMENT.map((value) => ({
  value,
  tokens: tokenize(value),
}));

interface ScoredEnumMatch {
  value: GymEquipment;
  score: number;
  overlap: number;
  discriminativeOverlap: number;
}

/**
 * BM25-inspired fuzzy match from a free-text equipment label (model hallucination or exercise
 * name) to the nearest [GymEquipment] enum. Returns null when confidence is below threshold.
 */
export function matchEquipmentEnum(raw: string): GymEquipment | null {
  const queryTokens = tokenize(raw);
  if (queryTokens.length === 0) {
    return null;
  }

  let best: ScoredEnumMatch | null = null;
  for (const entry of ENUM_CATALOG) {
    const candidate = scoreAgainst(raw, queryTokens, entry.tokens, entry.value);
    if (best === null || isBetterEnumMatch(candidate, best)) {
      best = candidate;
    }
  }

  return best !== null && best.score >= EQUIPMENT_ENUM_MATCH_THRESHOLD ? best.value : null;
}

function isBetterEnumMatch(candidate: ScoredEnumMatch, current: ScoredEnumMatch): boolean {
  if (candidate.discriminativeOverlap !== current.discriminativeOverlap) {
    return candidate.discriminativeOverlap > current.discriminativeOverlap;
  }
  if (candidate.overlap !== current.overlap) {
    return candidate.overlap > current.overlap;
  }
  return candidate.score > current.score;
}

function scoreAgainst(
  rawQuery: string,
  queryTokens: string[],
  docTokens: string[],
  docLabel: string,
): ScoredEnumMatch {
  if (docTokens.length === 0) {
    return { value: docLabel as GymEquipment, score: 0, overlap: 0, discriminativeOverlap: 0 };
  }

  const docTokenSet = new Set(docTokens);
  const matchedTokens = queryTokens.filter((token) => docTokenSet.has(token));
  const overlap = matchedTokens.length;
  const discriminativeOverlap = matchedTokens.filter(
    (token) => !GENERIC_EQUIPMENT_TOKENS.has(token),
  ).length;
  const levBonus = levenshteinBonus(rawQuery, docLabel);

  if (overlap === 0) {
    return {
      value: docLabel as GymEquipment,
      score: levBonus,
      overlap: 0,
      discriminativeOverlap: 0,
    };
  }

  const union = queryTokens.length + docTokens.length - overlap;
  const jaccard = overlap / Math.max(union, 1);
  const queryRecall = overlap / Math.max(queryTokens.length, 1);
  const docRecall = overlap / Math.max(docTokens.length, 1);
  const tokenScore = Math.max(jaccard, queryRecall, docRecall);
  return {
    value: docLabel as GymEquipment,
    score: tokenScore + levBonus,
    overlap,
    discriminativeOverlap,
  };
}

function levenshteinBonus(rawQuery: string, docLabel: string): number {
  const normalizedQuery = normalizeForMatching(rawQuery);
  const normalizedDoc = normalizeForMatching(docLabel);
  return levenshtein(normalizedQuery, normalizedDoc) <= LEVENSHTEIN_DISTANCE_THRESHOLD
    ? LEVENSHTEIN_BONUS
    : 0;
}

function normalizeForMatching(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenize(value: string): string[] {
  const camelSpaced = value.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
  return normalizeForMatching(camelSpaced)
    .split(" ")
    .filter((token) => token.length > 0);
}

function levenshtein(a: string, b: string): number {
  if (a === b) {
    return 0;
  }
  if (a.length === 0) {
    return b.length;
  }
  if (b.length === 0) {
    return a.length;
  }

  const costs = Array.from({ length: b.length + 1 }, (_, index) => index);
  for (let i = 1; i <= a.length; i += 1) {
    let last = i - 1;
    costs[0] = i;
    for (let j = 1; j <= b.length; j += 1) {
      const temp = costs[j];
      costs[j] = Math.min(
        costs[j] + 1,
        costs[j - 1] + 1,
        last + (a[i - 1] === b[j - 1] ? 0 : 1),
      );
      last = temp;
    }
  }
  return costs[b.length];
}
