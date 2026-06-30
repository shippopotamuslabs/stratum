/**
 * TABLE 7.7 rep ranges and RIR goals by exercise class.
 */
import type { RpRepProfile } from "./rpReferenceTables";
import { RP_REP_RIR_BANDS } from "./rpReferenceTables";
import type { LiftContext } from "./programPayloadContext";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

const LOWER_FREE_WEIGHT_PATTERNS = new Set(["SquatPattern", "HipHinge"]);

export function classifyLiftRepProfile(
  slot: WorkoutSlotTemplate,
  lift: LiftContext,
): RpRepProfile {
  if (slot.repProfile !== "isolation" && slot.repProfile !== "upperMachineOrPull") {
    if (
      LOWER_FREE_WEIGHT_PATTERNS.has(lift.movementPattern) &&
      isLowerFreeWeightName(lift.name)
    ) {
      return "lowerFreeWeightCompound";
    }
    return slot.repProfile;
  }
  if (slot.repProfile === "isolation") return "isolation";
  return "upperMachineOrPull";
}

function isLowerFreeWeightName(name: string): boolean {
  const n = name.toLowerCase();
  if (/\b(machine|smith)\b/.test(n)) return false;
  return /\b(barbell|squat|deadlift|rdl|romanian)\b/.test(n) || /\bsquat\b/.test(n);
}

export interface RepRirPlan {
  repMin: number;
  repMax: number;
  topSetRir: number;
  backOffRir: number;
}

export function repRirForSlot(
  slot: WorkoutSlotTemplate,
  lift: LiftContext,
): RepRirPlan {
  const profile = classifyLiftRepProfile(slot, lift);
  const band = RP_REP_RIR_BANDS[profile];
  return {
    repMin: band.repMin,
    repMax: band.repMax,
    topSetRir: band.topSetRir,
    backOffRir: Math.round((band.backOffRirMin + band.backOffRirMax) / 2),
  };
}
