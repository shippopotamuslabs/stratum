/**
 * Session-type slot skeletons (RP Table 7.16 / commercial UL templates).
 */
import type { GenerationContext } from "./programPayloadContext";
import type { RpRepProfile } from "./rpReferenceTables";

export type SlotRole = "anchor" | "secondary_compound" | "isolation" | "optional";

export type MuscleLengthBiasPreference = "Lengthened" | "Shortened" | "MidRange" | "any";

export interface WorkoutSlotTemplate {
  slotId: string;
  role: SlotRole;
  pattern: string;
  primaryMuscle: string;
  repProfile: RpRepProfile;
  lengthBiasPreference?: MuscleLengthBiasPreference;
  /** Sibling B sessions may prefer alternate row flavor. */
  siblingVariant?: "A" | "B" | "any";
}

export type SessionTypeId =
  | "Lower"
  | "Upper"
  | "UpperPush"
  | "UpperPull"
  | "FullBody"
  | "FullPush"
  | "FullPull"
  | "Chest"
  | "Back"
  | "Shoulders"
  | "Arms"
  | "QuadsCalves"
  | "GlutesHams"
  | "ChestTris"
  | "BackBis";

const LOWER_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "squat", role: "anchor", pattern: "SquatPattern", primaryMuscle: "Quad", repProfile: "lowerFreeWeightCompound" },
  { slotId: "hinge", role: "anchor", pattern: "HipHinge", primaryMuscle: "Hamstring", repProfile: "lowerFreeWeightCompound" },
  { slotId: "quad-iso", role: "isolation", pattern: "KneeExtension", primaryMuscle: "Quad", repProfile: "isolation" },
  { slotId: "ham-iso", role: "isolation", pattern: "KneeFlexion", primaryMuscle: "Hamstring", repProfile: "isolation" },
  { slotId: "calf", role: "optional", pattern: "PlantarFlexion", primaryMuscle: "Calf", repProfile: "isolation" },
];

const UPPER_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "h-press", role: "anchor", pattern: "HorizontalPress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "v-pull", role: "secondary_compound", pattern: "VerticalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull", siblingVariant: "A" },
  { slotId: "h-pull", role: "secondary_compound", pattern: "HorizontalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull", lengthBiasPreference: "Lengthened", siblingVariant: "A" },
  { slotId: "incline", role: "secondary_compound", pattern: "InclinePress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress", siblingVariant: "B" },
  { slotId: "lat-raise", role: "isolation", pattern: "ShoulderAbduction", primaryMuscle: "LateralDeltoid", repProfile: "isolation" },
  { slotId: "bicep", role: "isolation", pattern: "ElbowFlexion", primaryMuscle: "Bicep", repProfile: "isolation" },
  { slotId: "tricep", role: "isolation", pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" },
  { slotId: "rear-delt", role: "optional", pattern: "ShoulderHorizontalAbduction", primaryMuscle: "PosteriorDeltoid", repProfile: "isolation" },
];

const UPPER_PUSH_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "h-press", role: "anchor", pattern: "HorizontalPress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "v-press", role: "secondary_compound", pattern: "VerticalPress", primaryMuscle: "AnteriorDeltoid", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "incline", role: "secondary_compound", pattern: "InclinePress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "lat-raise", role: "isolation", pattern: "ShoulderAbduction", primaryMuscle: "LateralDeltoid", repProfile: "isolation" },
  { slotId: "tricep", role: "isolation", pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" },
];

const UPPER_PULL_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "v-pull", role: "anchor", pattern: "VerticalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull" },
  { slotId: "h-pull", role: "secondary_compound", pattern: "HorizontalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull", lengthBiasPreference: "Lengthened" },
  { slotId: "rear-delt", role: "isolation", pattern: "ShoulderHorizontalAbduction", primaryMuscle: "PosteriorDeltoid", repProfile: "isolation" },
  { slotId: "bicep", role: "isolation", pattern: "ElbowFlexion", primaryMuscle: "Bicep", repProfile: "isolation" },
];

const FULL_BODY_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "squat", role: "anchor", pattern: "SquatPattern", primaryMuscle: "Quad", repProfile: "lowerFreeWeightCompound" },
  { slotId: "h-press", role: "anchor", pattern: "HorizontalPress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "h-pull", role: "secondary_compound", pattern: "HorizontalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull" },
  { slotId: "hinge", role: "secondary_compound", pattern: "HipHinge", primaryMuscle: "Hamstring", repProfile: "lowerFreeWeightCompound" },
];

const FULL_PUSH_SLOTS: WorkoutSlotTemplate[] = [
  ...UPPER_PUSH_SLOTS,
  { slotId: "quad-iso", role: "isolation", pattern: "KneeExtension", primaryMuscle: "Quad", repProfile: "isolation" },
  { slotId: "calf", role: "optional", pattern: "PlantarFlexion", primaryMuscle: "Calf", repProfile: "isolation" },
];

const FULL_PULL_SLOTS: WorkoutSlotTemplate[] = [
  ...UPPER_PULL_SLOTS,
  { slotId: "hinge", role: "anchor", pattern: "HipHinge", primaryMuscle: "Hamstring", repProfile: "lowerFreeWeightCompound" },
  { slotId: "ham-iso", role: "isolation", pattern: "KneeFlexion", primaryMuscle: "Hamstring", repProfile: "isolation" },
];

const CHEST_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "h-press", role: "anchor", pattern: "HorizontalPress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "incline", role: "secondary_compound", pattern: "InclinePress", primaryMuscle: "Chest", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "fly", role: "isolation", pattern: "HorizontalPress", primaryMuscle: "Chest", repProfile: "isolation" },
];

const BACK_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "v-pull", role: "anchor", pattern: "VerticalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull" },
  { slotId: "h-pull", role: "secondary_compound", pattern: "HorizontalPull", primaryMuscle: "Back", repProfile: "upperMachineOrPull" },
  { slotId: "rear-delt", role: "optional", pattern: "ShoulderHorizontalAbduction", primaryMuscle: "PosteriorDeltoid", repProfile: "isolation" },
];

const SHOULDERS_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "v-press", role: "anchor", pattern: "VerticalPress", primaryMuscle: "AnteriorDeltoid", repProfile: "lowerMachineOrUpperPress" },
  { slotId: "lat-raise", role: "isolation", pattern: "ShoulderAbduction", primaryMuscle: "LateralDeltoid", repProfile: "isolation" },
  { slotId: "rear-delt", role: "isolation", pattern: "ShoulderHorizontalAbduction", primaryMuscle: "PosteriorDeltoid", repProfile: "isolation" },
];

const ARMS_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "bicep", role: "anchor", pattern: "ElbowFlexion", primaryMuscle: "Bicep", repProfile: "isolation" },
  { slotId: "tricep", role: "anchor", pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" },
];

const QUADS_CALVES_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "squat", role: "anchor", pattern: "SquatPattern", primaryMuscle: "Quad", repProfile: "lowerFreeWeightCompound" },
  { slotId: "quad-iso", role: "isolation", pattern: "KneeExtension", primaryMuscle: "Quad", repProfile: "isolation" },
  { slotId: "calf", role: "isolation", pattern: "PlantarFlexion", primaryMuscle: "Calf", repProfile: "isolation" },
];

const GLUTES_HAMS_SLOTS: WorkoutSlotTemplate[] = [
  { slotId: "hinge", role: "anchor", pattern: "HipHinge", primaryMuscle: "Hamstring", repProfile: "lowerFreeWeightCompound" },
  { slotId: "ham-iso", role: "isolation", pattern: "KneeFlexion", primaryMuscle: "Hamstring", repProfile: "isolation" },
];

const CHEST_TRIS_SLOTS: WorkoutSlotTemplate[] = [
  ...CHEST_SLOTS,
  { slotId: "tricep", role: "isolation", pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" },
];

const BACK_BIS_SLOTS: WorkoutSlotTemplate[] = [
  ...BACK_SLOTS,
  { slotId: "bicep", role: "isolation", pattern: "ElbowFlexion", primaryMuscle: "Bicep", repProfile: "isolation" },
];

const SESSION_SLOTS: Record<SessionTypeId, WorkoutSlotTemplate[]> = {
  Lower: LOWER_SLOTS,
  Upper: UPPER_SLOTS,
  UpperPush: UPPER_PUSH_SLOTS,
  UpperPull: UPPER_PULL_SLOTS,
  FullBody: FULL_BODY_SLOTS,
  FullPush: FULL_PUSH_SLOTS,
  FullPull: FULL_PULL_SLOTS,
  Chest: CHEST_SLOTS,
  Back: BACK_SLOTS,
  Shoulders: SHOULDERS_SLOTS,
  Arms: ARMS_SLOTS,
  QuadsCalves: QUADS_CALVES_SLOTS,
  GlutesHams: GLUTES_HAMS_SLOTS,
  ChestTris: CHEST_TRIS_SLOTS,
  BackBis: BACK_BIS_SLOTS,
};

export function slotsForSessionType(
  sessionType: SessionTypeId,
  context?: GenerationContext,
): WorkoutSlotTemplate[] {
  const base = SESSION_SLOTS[sessionType].map((s) => ({ ...s }));
  if (context == null) return base;

  promoteSpecializedSlots(base, context);

  if (sessionType === "FullBody" && armsSpecialized(context)) {
    base.push(
      { slotId: "bicep-fb", role: "isolation", pattern: "ElbowFlexion", primaryMuscle: "Bicep", repProfile: "isolation" },
      { slotId: "tricep-fb", role: "isolation", pattern: "ElbowExtension", primaryMuscle: "Tricep", repProfile: "isolation" },
    );
  }
  if (sessionType === "FullBody" && calfSpecialized(context)) {
    base.push(
      { slotId: "calf-fb", role: "isolation", pattern: "PlantarFlexion", primaryMuscle: "Calf", repProfile: "isolation" },
    );
  }
  return base;
}

function calfSpecialized(context: GenerationContext): boolean {
  return (context.specialization.Calf ?? "Maintenance") === "Specialize";
}

function promoteSpecializedSlots(slots: WorkoutSlotTemplate[], context: GenerationContext): void {
  for (const slot of slots) {
    if (slot.role !== "optional") continue;
    if ((context.specialization[slot.primaryMuscle] ?? "Maintenance") === "Specialize") {
      slot.role = "isolation";
    }
  }
}

function armsSpecialized(context: GenerationContext): boolean {
  return (
    (context.specialization.Bicep ?? "Maintenance") === "Specialize" ||
    (context.specialization.Tricep ?? "Maintenance") === "Specialize"
  );
}

export function displayNameForSessionType(sessionType: SessionTypeId, index: number): string {
  const labels: Record<SessionTypeId, string> = {
    Lower: "Lower",
    Upper: "Upper",
    UpperPush: "Upper Push",
    UpperPull: "Upper Pull",
    FullBody: "Full Body",
    FullPush: "Full Push",
    FullPull: "Full Pull",
    Chest: "Chest",
    Back: "Back",
    Shoulders: "Shoulders",
    Arms: "Arms",
    QuadsCalves: "Quads & Calves",
    GlutesHams: "Glutes & Hams",
    ChestTris: "Chest & Tris",
    BackBis: "Back & Bis",
  };
  const base = labels[sessionType];
  const letter = index % 2 === 0 ? "A" : "B";
  if (["Lower", "Upper", "UpperPush", "UpperPull", "FullBody", "FullPush", "FullPull"].includes(sessionType)) {
    return `${base} ${letter}`;
  }
  return base;
}

/** Primary muscles trained on a session type (for recovery scoring). */
export function primaryMusclesForSessionType(sessionType: SessionTypeId): string[] {
  const slots = SESSION_SLOTS[sessionType];
  return [...new Set(slots.map((s) => s.primaryMuscle))];
}
