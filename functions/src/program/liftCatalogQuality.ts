/**
 * Slot-aware lift quality adjustments — demote obscure or suboptimal catalog entries
 * and prefer bread-and-butter alternatives for program generation.
 */
import type { LiftContext } from "./programPayloadContext";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

const LIFT_ID_PENALTIES: Record<string, number> = {
  "decline-barbell-bench-press": 0.4,
  "dip-machine": 0.35,
  "one-leg-barbell-squat": 0.45,
  "standing-barbell-press-behind-neck": 0.5,
};

const LIFT_ID_BONUSES: Record<string, number> = {
  "ez-bar-skull-crusher": 0.25,
  "cable-rope-triceps-pushdown": 0.25,
  "cable-one-arm-triceps-extension": 0.2,
  "machine-triceps-extension": 0.15,
  "machine-shoulder-military-press": 0.2,
  "smith-machine-overhead-shoulder-press": 0.15,
  "bulgarian-split-squat": 0.15,
  "barbell-full-squat": 0.1,
  "barbell-front-squat": 0.1,
};

function namePenalty(name: string, pattern: string, slot?: WorkoutSlotTemplate): number {
  const n = name.toLowerCase();
  let penalty = 0;

  if (/\bdecline\b/.test(n) && !/\bincline\b/.test(n)) {
    penalty += 0.35;
  }
  if (/\bbehind.?neck\b/.test(n) || /\bbehind the neck\b/.test(n)) {
    penalty += 0.45;
  }
  if (/\bdip machine\b/.test(n) || (/\bdips?\b/.test(n) && /\bmachine\b/.test(n))) {
    penalty += slot?.pattern === "ElbowExtension" ? 0.4 : 0.15;
  }
  if (/\bone.?leg\b/.test(n) && pattern === "SquatPattern") {
    penalty += 0.4;
  }
  if (/\bguillotine\b/.test(n)) penalty += 0.42;
  if (/\bwide stance\b/.test(n)) penalty += 0.2;
  if (/^(alternating|alternate)\b/.test(n)) {
    penalty += 0.15;
  }

  return penalty;
}

function nameBonus(name: string, pattern: string, slot?: WorkoutSlotTemplate): number {
  const n = name.toLowerCase();
  let bonus = 0;

  if (pattern === "HorizontalPress" && /\b(bench press|barbell bench)\b/.test(n) && !/\bdecline\b/.test(n)) {
    bonus += 0.12;
  }
  if (pattern === "InclinePress" && /\bincline\b/.test(n)) {
    bonus += 0.1;
  }
  if (pattern === "VerticalPress") {
    if (/\b(dumbbell|db)\b.*\bpress\b/.test(n) || /\bmachine\b.*\b(military|shoulder|press)\b/.test(n)) {
      bonus += 0.18;
    }
    if (/\b(overhead|military)\b.*\bpress\b/.test(n) && !/\bbehind\b/.test(n)) {
      bonus += 0.12;
    }
  }
  if (slot?.pattern === "ElbowExtension" || pattern === "ElbowExtension") {
    if (/\b(skull|skullcrusher|skull crusher)\b/.test(n)) bonus += 0.22;
    if (/\b(tricep extension|triceps extension)\b/.test(n) && /\b(cable|rope|dumbbell|db)\b/.test(n)) {
      bonus += 0.2;
    }
    if (/\bpush.?down\b/.test(n)) bonus += 0.15;
  }
  if (pattern === "SquatPattern") {
    if (/\b(front squat|back squat|barbell squat|full squat)\b/.test(n)) bonus += 0.15;
    if (/\b(bulgarian|split squat)\b/.test(n)) bonus += 0.08;
  }

  return bonus;
}

/** Net quality adjustment blended into lift priority (negative = demote). */
export function liftCatalogQualityAdjustment(
  lift: LiftContext,
  slot?: WorkoutSlotTemplate,
): number {
  const pattern = lift.movementPattern;
  let adjustment = 0;

  adjustment -= LIFT_ID_PENALTIES[lift.id] ?? 0;
  adjustment += LIFT_ID_BONUSES[lift.id] ?? 0;
  adjustment -= namePenalty(lift.name, pattern, slot);
  adjustment += nameBonus(lift.name, pattern, slot);

  if (lift.mainstream === false) {
    adjustment -= 0.08;
  }

  return adjustment;
}
