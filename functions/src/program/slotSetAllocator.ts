/**
 * Distributes weekly muscle volume across session slots with ≤8 sets/muscle/workout cap.
 */
import type { GenerationContext } from "./programPayloadContext";
import type { WeeklyVolumeTargets } from "./volumeTargetPlanner";
import type { ResolvedSessionPlan } from "./splitResolver";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";
import {
  PRIMARY_SET_CREDIT,
  PATTERN_SECONDARY_MUSCLE_CREDITS,
  SESSION_MUSCLE_SET_CAP,
  MIN_SPECIALIZED_DIRECT_SETS,
  sessionHardSetCapForLength,
} from "./rpReferenceTables";

export interface FilledSlot extends WorkoutSlotTemplate {
  setCount: number;
}

export interface SessionSlotPlan extends ResolvedSessionPlan {
  filledSlots: FilledSlot[];
}

/** Max sets on one isolation slot per session — spreads specialized volume across sessions. */
const SPECIALIZED_SETS_PER_SLOT_CAP = 6;

/** Max sets on one compound slot — prevents 6-set bench stacks on a single lift. */
const COMPOUND_SETS_PER_SLOT_CAP = 4;

/** Minimum sets on anchor compounds (squat/hinge/press) when the slot is active. */
const ANCHOR_MIN_SETS = 3;

/** Minimum direct arm isolation on a dedicated push/pull day when compounds also train that arm. */
const MIN_ARM_ISO_ON_SPLIT_DAY = 3;

/** Minimum sets on a secondary compound (e.g. vertical press) when the session is active. */
const SECONDARY_COMPOUND_MIN_SETS = 2;

/** Max set-count spread between sibling lower days before rebalancing. */
const LOWER_SESSION_SPREAD_TARGET = 3;

const LOWER_SLOT_BALANCE_ORDER = ["squat", "hinge", "quad-iso", "ham-iso", "calf"] as const;

const LOWER_FILL_PATTERN_ORDER = [
  "SquatPattern",
  "HipHinge",
  "KneeExtension",
  "KneeFlexion",
  "PlantarFlexion",
] as const;

function sessionHardSetCap(context: GenerationContext): number | null {
  return sessionHardSetCapForLength(context.sessionLengthMinutes);
}

function muscleCreditsInSession(plan: SessionSlotPlan, muscle: string): number {
  let total = 0;
  for (const slot of plan.filledSlots) {
    if (slot.setCount <= 0) continue;
    if (slot.primaryMuscle === muscle) {
      total += slot.setCount * PRIMARY_SET_CREDIT;
      continue;
    }
    for (const secondary of PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern] ?? []) {
      if (secondary.muscle === muscle) {
        total += slot.setCount * secondary.creditPerSet;
      }
    }
  }
  return total;
}

function allocationOrder(
  weeklyTargets: WeeklyVolumeTargets,
  context: GenerationContext,
): Array<[string, WeeklyVolumeTargets extends Map<string, infer T> ? T : never]> {
  return [...weeklyTargets.entries()].sort(([muscleA], [muscleB]) => {
    const aSpec = (context.specialization[muscleA] ?? "Maintenance") === "Specialize";
    const bSpec = (context.specialization[muscleB] ?? "Maintenance") === "Specialize";
    if (aSpec !== bSpec) return aSpec ? -1 : 1;
    return muscleA.localeCompare(muscleB);
  });
}

export function allocateSlotSetCounts(
  sessions: ResolvedSessionPlan[],
  weeklyTargets: WeeklyVolumeTargets,
  context: GenerationContext,
): SessionSlotPlan[] {
  const plans: SessionSlotPlan[] = sessions.map((session) => ({
    ...session,
    filledSlots: session.slots
      .filter((s) => s.role !== "optional" || shouldIncludeOptional(s, context))
      .map((s) => ({ ...s, setCount: 0 })),
  }));

  for (const [muscle, target] of allocationOrder(weeklyTargets, context)) {
    if (target.aim <= 0) continue;
    const isSpecialized = (context.specialization[muscle] ?? "Maintenance") === "Specialize";
    const slotsWithMuscle: Array<{ plan: SessionSlotPlan; slot: FilledSlot; idx: number }> = [];
    plans.forEach((plan) => {
      plan.filledSlots.forEach((slot, idx) => {
        if (slot.primaryMuscle === muscle) {
          slotsWithMuscle.push({ plan, slot, idx });
        }
      });
    });
    if (slotsWithMuscle.length === 0) continue;

    const remaining = Math.round(target.aim);
    const primarySlots = slotsWithMuscle.filter((s) => s.slot.primaryMuscle === muscle);
    const targets = primarySlots.length > 0 ? primarySlots : slotsWithMuscle;

    if (isSpecialized) {
      distributeWithPerSlotCap(targets, remaining, SPECIALIZED_SETS_PER_SLOT_CAP, muscle);
    } else {
      distributeWithPerSlotCap(targets, remaining, COMPOUND_SETS_PER_SLOT_CAP, muscle);
    }
  }

  balanceSessionSlotSets(plans, context);
  rebalanceArmSpecSplitSessions(plans, context);
  balanceLowerSessionSpread(plans, context);
  fillLowerSessionsToSessionCap(plans, weeklyTargets, context);
  ensureAnchorMinimumSets(plans);
  enforceSessionCaps(plans, context);
  applyFullBodyShortSessionLayout(plans, context);
  return plans;
}

function distributeWithPerSlotCap(
  targets: Array<{ plan: SessionSlotPlan; slot: FilledSlot }>,
  remaining: number,
  perSlotCap: number,
  muscle: string,
): void {
  let slotIdx = 0;
  let guard = 0;
  while (remaining > 0 && targets.length > 0 && guard < targets.length * 40) {
    guard++;
    const entry = targets[slotIdx % targets.length]!;
    if (muscleCreditsInSession(entry.plan, muscle) >= SESSION_MUSCLE_SET_CAP) {
      slotIdx++;
      continue;
    }
    const headroom = Math.min(perSlotCap - entry.slot.setCount, SESSION_MUSCLE_SET_CAP - muscleCreditsInSession(entry.plan, muscle));
    if (headroom <= 0) {
      slotIdx++;
      continue;
    }
    const add = Math.min(remaining, headroom);
    entry.slot.setCount += add;
    remaining -= add;
    slotIdx++;
  }
}

function ensureAnchorMinimumSets(plans: SessionSlotPlan[]): void {
  for (const plan of plans) {
    for (const slot of plan.filledSlots) {
      if (slot.role === "anchor" && slot.setCount > 0 && slot.setCount < ANCHOR_MIN_SETS) {
        slot.setCount = ANCHOR_MIN_SETS;
      }
    }
  }
}

function enforceSessionCaps(plans: SessionSlotPlan[], context: GenerationContext): void {
  const cap = sessionHardSetCap(context);
  for (const plan of plans) {
    if (cap != null) {
      let totalSets = plan.filledSlots.reduce((s, sl) => s + sl.setCount, 0);
      while (totalSets > cap && plan.filledSlots.length > 0) {
        const drop = slotToDropForSessionTimeCap(plan, context);
        if (!drop) break;
        drop.setCount -= 1;
        totalSets -= 1;
      }
    }
    for (const muscle of musclesNeedingCapEnforcement(plan)) {
      while (muscleCreditsInSession(plan, muscle) > SESSION_MUSCLE_SET_CAP) {
        const drop = slotToTrimForMuscleCap(plan, muscle, context);
        if (!drop) break;
        drop.setCount -= 1;
      }
    }
  }
}

function musclesNeedingCapEnforcement(plan: SessionSlotPlan): Set<string> {
  const muscles = new Set(plan.filledSlots.map((s) => s.primaryMuscle));
  for (const slot of plan.filledSlots) {
    for (const secondary of PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern] ?? []) {
      muscles.add(secondary.muscle);
    }
  }
  return muscles;
}

function specializedMuscles(context: GenerationContext): Set<string> {
  const out = new Set<string>();
  for (const [muscle, level] of Object.entries(context.specialization)) {
    if (level === "Specialize") out.add(muscle);
  }
  return out;
}

function slotToDropForSessionTimeCap(plan: SessionSlotPlan, context: GenerationContext): FilledSlot | undefined {
  const specialized = specializedMuscles(context);
  const optional = plan.filledSlots.find(
    (s) => s.role === "optional" && s.setCount > 0 && !specialized.has(s.primaryMuscle),
  );
  if (optional) return optional;

  const specializedOptional = plan.filledSlots.find(
    (s) =>
      s.role === "optional"
      && s.setCount > MIN_SPECIALIZED_DIRECT_SETS
      && specialized.has(s.primaryMuscle),
  );
  if (specializedOptional) return specializedOptional;

  const maintenanceIso = plan.filledSlots.find(
    (s) => s.role === "isolation" && s.setCount > 0 && !specialized.has(s.primaryMuscle),
  );
  if (maintenanceIso) return maintenanceIso;

  const maintenanceSecondary = [...plan.filledSlots]
    .reverse()
    .find((s) => s.setCount > 0 && s.role === "secondary_compound" && !specialized.has(s.primaryMuscle));
  if (maintenanceSecondary) return maintenanceSecondary;

  const specializedAboveMin = plan.filledSlots.find(
    (s) => s.setCount > MIN_SPECIALIZED_DIRECT_SETS
      && specialized.has(s.primaryMuscle)
      && (s.role === "isolation" || s.role === "anchor"),
  );
  if (specializedAboveMin) return specializedAboveMin;

  const anchorWithSets = plan.filledSlots.find((s) => s.role === "anchor" && s.setCount > 1);
  if (anchorWithSets) return anchorWithSets;

  return plan.filledSlots.find((s) => s.setCount > 0);
}

function slotToTrimForMuscleCap(
  plan: SessionSlotPlan,
  muscle: string,
  context: GenerationContext,
): FilledSlot | undefined {
  const isSpecialized = (context.specialization[muscle] ?? "Maintenance") === "Specialize";
  const candidates = plan.filledSlots.filter((slot) => {
    if (slot.setCount <= 0) return false;
    if (slot.primaryMuscle === muscle) return true;
    return (PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern] ?? []).some((s) => s.muscle === muscle);
  });

  candidates.sort((a, b) => trimScore(a, muscle, isSpecialized) - trimScore(b, muscle, isSpecialized));
  return candidates[0];
}

function trimScore(slot: FilledSlot, muscle: string, isSpecializedMuscle: boolean): number {
  const isPrimary = slot.primaryMuscle === muscle;
  const isSecondary = !isPrimary
    && (PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern] ?? []).some((s) => s.muscle === muscle);

  if (isSpecializedMuscle) {
    if (isSecondary) return 0;
    if (isPrimary && slot.role === "isolation") return 3;
    if (isPrimary && slot.role === "anchor") return 5;
    return 2;
  }
  if (isPrimary && slot.role !== "anchor") return 0;
  if (isPrimary && slot.role === "anchor") return 4;
  return 1;
}

function shouldIncludeOptional(slot: WorkoutSlotTemplate, context: GenerationContext): boolean {
  if ((context.specialization[slot.primaryMuscle] ?? "Maintenance") === "Specialize") {
    return true;
  }
  if (context.sessionLengthMinutes == null) return true;
  return context.sessionLengthMinutes >= 60;
}

function findSlot(plan: SessionSlotPlan, slotId: string): FilledSlot | undefined {
  return plan.filledSlots.find((s) => s.slotId === slotId);
}

function findSlotByPattern(
  plan: SessionSlotPlan,
  pattern: string,
  role?: FilledSlot["role"],
): FilledSlot | undefined {
  return plan.filledSlots.find((s) => s.pattern === pattern && (role == null || s.role === role));
}

function sessionTotalSets(plan: SessionSlotPlan): number {
  return plan.filledSlots.reduce((sum, s) => sum + s.setCount, 0);
}

function transferSets(from: FilledSlot, to: FilledSlot, count: number): number {
  const moved = Math.min(count, from.setCount);
  if (moved <= 0) return 0;
  from.setCount -= moved;
  to.setCount += moved;
  return moved;
}

/** Rebalance stacked push/pull and leg pairs; prioritize specialized slots on push days. */
function balanceSessionSlotSets(plans: SessionSlotPlan[], context: GenerationContext): void {
  const cap = sessionHardSetCap(context);
  for (const plan of plans) {
    balancePushPullPair(plan, "HorizontalPress", "HorizontalPull");
    balancePushPullPair(plan, "HorizontalPress", "VerticalPull");
    balanceLegPair(plan);
    ensureUpperDayPullMinimum(plan);
    if (cap != null) {
      while (sessionTotalSets(plan) > cap) {
        const drop = slotToDropForSessionTimeCap(plan, context);
        if (!drop || drop.setCount <= 0) break;
        drop.setCount -= 1;
      }
    }
  }
}

function balancePushPullPair(plan: SessionSlotPlan, pushPattern: string, pullPattern: string): void {
  const push = findSlotByPattern(plan, pushPattern);
  const pull = findSlotByPattern(plan, pullPattern);
  if (!push || !pull || push.setCount <= 2 || pull.setCount > 0) return;

  const move = Math.min(Math.floor(push.setCount / 2), push.setCount - 2, COMPOUND_SETS_PER_SLOT_CAP);
  if (move >= 2) {
    transferSets(push, pull, move);
  } else if (push.setCount >= 3) {
    transferSets(push, pull, 1);
  }
}

function balanceLegPair(plan: SessionSlotPlan): void {
  const squat = findSlotByPattern(plan, "SquatPattern", "anchor")
    ?? findSlotByPattern(plan, "SquatPattern");
  const hinge = findSlotByPattern(plan, "HipHinge");
  if (!squat || !hinge || squat.setCount <= 3 || hinge.setCount > 0) return;

  const move = Math.min(2, squat.setCount - ANCHOR_MIN_SETS);
  if (move > 0) transferSets(squat, hinge, move);
}

function perSlotSetCap(slot: FilledSlot): number {
  if (slot.role === "anchor" || slot.role === "secondary_compound") return COMPOUND_SETS_PER_SLOT_CAP;
  return SPECIALIZED_SETS_PER_SLOT_CAP;
}

function minSetsForSlot(slot: FilledSlot): number {
  if (slot.role === "anchor") return ANCHOR_MIN_SETS;
  return 0;
}

function weeklyPrimarySetsForMuscle(plans: SessionSlotPlan[], muscle: string): number {
  let total = 0;
  for (const plan of plans) {
    for (const slot of plan.filledSlots) {
      if (slot.primaryMuscle === muscle) total += slot.setCount;
    }
  }
  return total;
}

/** Even out Lower A / Lower B totals — arm specialization does not affect leg weekly targets. */
function balanceLowerSessionSpread(plans: SessionSlotPlan[], context: GenerationContext): void {
  const cap = sessionHardSetCap(context);
  const lowerPlans = plans.filter((p) => p.sessionType === "Lower");
  if (lowerPlans.length < 2 || cap == null) return;

  for (let guard = 0; guard < 100; guard++) {
    const totals = lowerPlans.map((p) => sessionTotalSets(p));
    const maxTotal = Math.max(...totals);
    const minTotal = Math.min(...totals);
    if (maxTotal - minTotal <= LOWER_SESSION_SPREAD_TARGET) return;

    const maxPlan = lowerPlans[totals.indexOf(maxTotal)]!;
    const minPlan = lowerPlans[totals.indexOf(minTotal)]!;
    if (minTotal >= cap) return;

    let moved = false;
    for (const slotId of LOWER_SLOT_BALANCE_ORDER) {
      const from = findSlot(maxPlan, slotId);
      const to = findSlot(minPlan, slotId);
      if (!from || !to || from.setCount <= minSetsForSlot(from)) continue;
      if (to.setCount >= perSlotSetCap(to)) continue;
      from.setCount -= 1;
      to.setCount += 1;
      moved = true;
      break;
    }
    if (!moved) return;
  }
}

/** Top up under-filled lower days toward the session set cap without exceeding weekly muscle max. */
function fillLowerSessionsToSessionCap(
  plans: SessionSlotPlan[],
  weeklyTargets: WeeklyVolumeTargets,
  context: GenerationContext,
): void {
  const cap = sessionHardSetCap(context);
  if (cap == null) return;

  for (const plan of plans.filter((p) => p.sessionType === "Lower")) {
    for (let guard = 0; guard < 200 && sessionTotalSets(plan) < cap; guard++) {
      let added = false;
      for (const pattern of LOWER_FILL_PATTERN_ORDER) {
        const slot = findSlotByPattern(plan, pattern);
        if (!slot || slot.setCount >= perSlotSetCap(slot)) continue;

        const target = weeklyTargets.get(slot.primaryMuscle);
        if (target != null && weeklyPrimarySetsForMuscle(plans, slot.primaryMuscle) >= target.max) continue;
        if (muscleCreditsInSession(plan, slot.primaryMuscle) >= SESSION_MUSCLE_SET_CAP) continue;

        slot.setCount += 1;
        added = true;
        break;
      }
      if (!added) break;
    }
  }
}

function armsSpecializedInContext(context: GenerationContext): boolean {
  return (
    (context.specialization.Bicep ?? "Maintenance") === "Specialize" ||
    (context.specialization.Tricep ?? "Maintenance") === "Specialize"
  );
}

function applyFullBodyShortSessionLayout(plans: SessionSlotPlan[], context: GenerationContext): void {
  for (const plan of plans) {
    capFullBodyArmVolumeOnShortSessions(plan, context);
  }
}

function capFullBodyArmVolumeOnShortSessions(plan: SessionSlotPlan, context: GenerationContext): void {
  if (plan.sessionType !== "FullBody" || !armsSpecializedInContext(context)) return;
  const cap = sessionHardSetCap(context);
  if (cap == null || cap > 12) return;

  const bicep = plan.filledSlots.find((s) => s.primaryMuscle === "Bicep");
  const tricep = plan.filledSlots.find((s) => s.primaryMuscle === "Tricep");
  const press = findSlotByPattern(plan, "HorizontalPress");
  const pull = findSlotByPattern(plan, "HorizontalPull");
  const squat = findSlotByPattern(plan, "SquatPattern");

  if (bicep) bicep.setCount = 2;
  if (tricep) tricep.setCount = 2;
  if (press) press.setCount = 2;
  if (pull) pull.setCount = 2;
  if (squat) squat.setCount = 2;

  for (const slot of plan.filledSlots) {
    if (slot === bicep || slot === tricep || slot === press || slot === pull || slot === squat) continue;
    slot.setCount = 0;
  }
}

function ensureUpperDayPullMinimum(plan: SessionSlotPlan): void {
  if (plan.sessionType !== "Upper") return;
  const press = findSlotByPattern(plan, "HorizontalPress");
  const pull = findSlotByPattern(plan, "VerticalPull") ?? findSlotByPattern(plan, "HorizontalPull");
  if (!press || !pull || press.setCount === 0 || pull.setCount >= 2) return;
  if (press.setCount <= 2) return;
  transferSets(press, pull, Math.min(2 - pull.setCount, press.setCount - 2));
}

/**
 * On push/pull split days, direct arm isolation can consume the whole per-session muscle
 * credit budget and zero out compounds (presses credit triceps; rows credit biceps).
 * Reserve compound minimums first, then cap arm isolation to remaining headroom.
 */
function rebalanceArmSpecSplitSessions(plans: SessionSlotPlan[], context: GenerationContext): void {
  for (const plan of plans) {
    if (plan.sessionType === "UpperPush" && (context.specialization.Tricep ?? "Maintenance") === "Specialize") {
      rebalanceUpperPushArmSpec(plan, context);
    } else if (plan.sessionType === "UpperPull" && (context.specialization.Bicep ?? "Maintenance") === "Specialize") {
      rebalanceUpperPullArmSpec(plan, context);
    } else if (plan.sessionType === "Upper") {
      rebalanceCombinedUpperArmSpec(plan, context);
    }
  }
}

function secondaryCreditsForMuscle(plan: SessionSlotPlan, muscle: string): number {
  let total = 0;
  for (const slot of plan.filledSlots) {
    if (slot.setCount <= 0 || slot.primaryMuscle === muscle) continue;
    for (const secondary of PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern] ?? []) {
      if (secondary.muscle === muscle) {
        total += slot.setCount * secondary.creditPerSet;
      }
    }
  }
  return total;
}

function transferToReachMinimum(
  from: FilledSlot | undefined,
  to: FilledSlot | undefined,
  minimum: number,
  floorForDonor: number,
): void {
  if (!from || !to) return;
  const needed = minimum - to.setCount;
  if (needed <= 0) return;
  transferSets(from, to, Math.min(needed, Math.max(0, from.setCount - floorForDonor)));
}

function capDirectArmIsolation(
  plan: SessionSlotPlan,
  armMuscle: "Bicep" | "Tricep",
  isolationPattern: "ElbowFlexion" | "ElbowExtension",
): void {
  const isolation = findSlotByPattern(plan, isolationPattern, "isolation");
  if (!isolation || isolation.setCount <= 0) return;

  const secondary = secondaryCreditsForMuscle(plan, armMuscle);
  const maxDirect = Math.floor(SESSION_MUSCLE_SET_CAP - secondary);
  if (maxDirect <= 0) {
    isolation.setCount = 0;
    return;
  }
  isolation.setCount = Math.min(isolation.setCount, maxDirect);
}

function rebalanceUpperPushArmSpec(plan: SessionSlotPlan, context: GenerationContext): void {
  const tricep = findSlotByPattern(plan, "ElbowExtension", "isolation");
  const hPress = findSlotByPattern(plan, "HorizontalPress");
  const incline = findSlotByPattern(plan, "InclinePress");
  const vPress = findSlotByPattern(plan, "VerticalPress");
  const latRaise = findSlotByPattern(plan, "ShoulderAbduction", "isolation");

  transferToReachMinimum(tricep, hPress, ANCHOR_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);
  transferToReachMinimum(tricep, incline, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);
  transferToReachMinimum(tricep, vPress, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);
  transferToReachMinimum(tricep, latRaise, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);

  capDirectArmIsolation(plan, "Tricep", "ElbowExtension");

  const cap = sessionHardSetCap(context);
  if (cap != null) {
    while (sessionTotalSets(plan) > cap) {
      const drop = slotToDropForSessionTimeCap(plan, context);
      if (!drop || drop.setCount <= 0) break;
      drop.setCount -= 1;
    }
  }
}

function rebalanceUpperPullArmSpec(plan: SessionSlotPlan, context: GenerationContext): void {
  const bicep = findSlotByPattern(plan, "ElbowFlexion", "isolation");
  const vPull = findSlotByPattern(plan, "VerticalPull");
  const hPull = findSlotByPattern(plan, "HorizontalPull");
  const rearDelt = findSlotByPattern(plan, "ShoulderHorizontalAbduction", "isolation");

  transferToReachMinimum(bicep, vPull, ANCHOR_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);
  transferToReachMinimum(bicep, hPull, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);
  transferToReachMinimum(bicep, rearDelt, SECONDARY_COMPOUND_MIN_SETS, MIN_ARM_ISO_ON_SPLIT_DAY);

  capDirectArmIsolation(plan, "Bicep", "ElbowFlexion");

  const cap = sessionHardSetCap(context);
  if (cap != null) {
    while (sessionTotalSets(plan) > cap) {
      const drop = slotToDropForSessionTimeCap(plan, context);
      if (!drop || drop.setCount <= 0) break;
      drop.setCount -= 1;
    }
  }
}

function rebalanceCombinedUpperArmSpec(plan: SessionSlotPlan, context: GenerationContext): void {
  const bicepSpec = (context.specialization.Bicep ?? "Maintenance") === "Specialize";
  const tricepSpec = (context.specialization.Tricep ?? "Maintenance") === "Specialize";
  if (!bicepSpec && !tricepSpec) return;

  const bicep = findSlotByPattern(plan, "ElbowFlexion", "isolation");
  const tricep = findSlotByPattern(plan, "ElbowExtension", "isolation");
  const vPull = findSlotByPattern(plan, "VerticalPull");
  const hPull = findSlotByPattern(plan, "HorizontalPull");
  const hPress = findSlotByPattern(plan, "HorizontalPress");

  const armDonorFloor = MIN_ARM_ISO_ON_SPLIT_DAY;
  if (bicepSpec) {
    transferToReachMinimum(bicep, vPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor);
    transferToReachMinimum(bicep, hPull, SECONDARY_COMPOUND_MIN_SETS, armDonorFloor);
    capDirectArmIsolation(plan, "Bicep", "ElbowFlexion");
  }
  if (tricepSpec) {
    transferToReachMinimum(tricep, hPress, ANCHOR_MIN_SETS, armDonorFloor);
    capDirectArmIsolation(plan, "Tricep", "ElbowExtension");
  }

  ensureUpperDayPullMinimum(plan);
}
