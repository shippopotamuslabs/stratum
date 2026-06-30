/**
 * Deterministic lift selection per slot with anchor-quality scoring.
 */
import type { GenerationContext, LiftContext } from "./programPayloadContext";
import { scoreLift, compareLiftCandidates, mainstreamNameScore } from "./liftPriority";
import { liftCatalogQualityAdjustment } from "./liftCatalogQuality";
import type { FilledSlot } from "./slotSetAllocator";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

export interface SelectedSlotLift {
  slot: FilledSlot;
  lift: LiftContext;
  candidates: LiftContext[];
}

function matchesPattern(lift: LiftContext, pattern: string): boolean {
  return lift.movementPattern === pattern || lift.movementPatterns.includes(pattern);
}

function slotRoleBonus(slot: WorkoutSlotTemplate, lift: LiftContext): number {
  let bonus = 0;
  const mainstream = lift.mainstream === true ? 1.0 : mainstreamNameScore(lift.name);
  if (slot.role === "anchor") {
    bonus += mainstream * 0.3;
    if (/\bbarbell\b/i.test(lift.name)) bonus += 0.15;
    if (/\b(life fitness|hammer strength|nautilus|precor)\b/i.test(lift.name)) bonus -= 0.2;
    if (lift.equipment && /\b(machine|brand)\b/i.test(lift.equipment)) bonus -= 0.1;
  } else if (slot.role === "isolation") {
    if (/\b(cable|machine)\b/i.test(lift.name)) bonus += 0.05;
  }
  return bonus;
}

function lengthBiasBonus(
  slot: WorkoutSlotTemplate,
  lift: LiftContext,
  siblingIndex: number,
): number {
  const pref = slot.lengthBiasPreference;
  if (!pref || pref === "any") return 0;
  const bias = lift.muscleLengthBias ?? "MidRange";
  if (slot.siblingVariant === "B" && siblingIndex % 2 === 1) {
    return bias === "Lengthened" ? 0.1 : bias === "Shortened" ? -0.05 : 0;
  }
  if (pref === "Lengthened" && bias === "Lengthened") return 0.1;
  if (pref === "Shortened" && bias === "Shortened") return 0.1;
  return 0;
}

export function scoreLiftForSlot(
  lift: LiftContext,
  slot: WorkoutSlotTemplate,
  context: GenerationContext,
  siblingIndex: number,
): number {
  return (
    scoreLift(lift, context) +
    slotRoleBonus(slot, lift) +
    lengthBiasBonus(slot, lift, siblingIndex) +
    liftCatalogQualityAdjustment(lift, slot)
  );
}

export function candidatesForSlot(
  slot: WorkoutSlotTemplate,
  context: GenerationContext,
): LiftContext[] {
  const out: LiftContext[] = [];
  for (const lift of context.liftsById.values()) {
    if (!context.enabledLiftIds.has(lift.id)) continue;
    if (!matchesPattern(lift, slot.pattern)) continue;
    if (slot.primaryMuscle) {
      const primaryMatch = lift.primaryMuscles.includes(slot.primaryMuscle);
      if (slot.role === "isolation" || slot.role === "anchor") {
        if (!primaryMatch) continue;
      } else if (!primaryMatch && !lift.secondaryMuscles.includes(slot.primaryMuscle)) {
        continue;
      }
    }
    out.push(lift);
  }
  return out;
}

export function selectLiftForSlot(
  slot: FilledSlot,
  context: GenerationContext,
  siblingIndex: number,
  usedLiftIds: Set<string>,
  minCycleGap: number,
  sessionIndex: number,
  lastUsedSession: Map<string, number>,
): SelectedSlotLift | null {
  if (slot.setCount <= 0) return null;

  const ranked = candidatesForSlot(slot, context)
    .filter((lift) => {
      const last = lastUsedSession.get(lift.id);
      if (last == null) return true;
      return sessionIndex - last >= minCycleGap || !usedLiftIds.has(lift.id);
    })
    .sort((a, b) => {
      const sa = scoreLiftForSlot(a, slot, context, siblingIndex);
      const sb = scoreLiftForSlot(b, slot, context, siblingIndex);
      if (sb !== sa) return sb - sa;
      return compareLiftCandidates(
        { liftId: a.id, name: a.name, pattern: a.movementPattern, priority: sa, priorityTier: a.proven ? "proven" : "available", isCompound: a.movementPatterns.length > 1, primaryMuscles: a.primaryMuscles, secondaryMuscles: a.secondaryMuscles },
        { liftId: b.id, name: b.name, pattern: b.movementPattern, priority: sb, priorityTier: b.proven ? "proven" : "available", isCompound: b.movementPatterns.length > 1, primaryMuscles: b.primaryMuscles, secondaryMuscles: b.secondaryMuscles },
      );
    });

  const topCandidates = ranked.slice(0, 5);
  const pick = topCandidates.find((l) => !usedLiftIds.has(l.id)) ?? topCandidates[0];
  if (!pick) return null;

  usedLiftIds.add(pick.id);
  lastUsedSession.set(pick.id, sessionIndex);
  return { slot, lift: pick, candidates: topCandidates };
}
