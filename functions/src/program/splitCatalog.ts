/**
 * TABLE 7.2 Hypertrophy Frequency Matrix — split template catalog.
 */
import type { FrequencyTier } from "./rpReferenceTables";
import type { SessionTypeId } from "./sessionTypeCatalog";

export interface SplitTemplate {
  id: string;
  trainingDaysPerWeek: number;
  /** Minimum frequency tier this split satisfies (TABLE 7.6). */
  frequencyTier: FrequencyTier;
  sessionTypes: SessionTypeId[];
  /** Default option when multiple splits match. */
  isDefault?: boolean;
}

export const SPLIT_CATALOG: SplitTemplate[] = [
  // 2 days
  { id: "2d-t1-ul", trainingDaysPerWeek: 2, frequencyTier: 1, sessionTypes: ["Upper", "Lower"], isDefault: true },
  { id: "2d-t1-pushpull", trainingDaysPerWeek: 2, frequencyTier: 1, sessionTypes: ["FullPush", "FullPull"] },
  { id: "2d-t2-fb", trainingDaysPerWeek: 2, frequencyTier: 2, sessionTypes: ["FullBody", "FullBody"], isDefault: true },

  // 3 days
  { id: "3d-t1-l-up-upull", trainingDaysPerWeek: 3, frequencyTier: 1, sessionTypes: ["Lower", "UpperPush", "UpperPull"], isDefault: true },
  { id: "3d-t2-l-u-fb", trainingDaysPerWeek: 3, frequencyTier: 2, sessionTypes: ["Lower", "Upper", "FullBody"], isDefault: true },
  { id: "3d-t2-fp-fpull-fb", trainingDaysPerWeek: 3, frequencyTier: 2, sessionTypes: ["FullPush", "FullPull", "FullBody"] },
  { id: "3d-t3-fb", trainingDaysPerWeek: 3, frequencyTier: 3, sessionTypes: ["FullBody", "FullBody", "FullBody"], isDefault: true },

  // 4 days
  { id: "4d-t1-bodypart", trainingDaysPerWeek: 4, frequencyTier: 1, sessionTypes: ["ChestTris", "BackBis", "Shoulders", "Lower"] },
  { id: "4d-t2-ul", trainingDaysPerWeek: 4, frequencyTier: 2, sessionTypes: ["Lower", "Upper", "Lower", "Upper"], isDefault: true },
  { id: "4d-t2-pushpull", trainingDaysPerWeek: 4, frequencyTier: 2, sessionTypes: ["FullPush", "FullPull", "FullPush", "FullPull"] },
  { id: "4d-t3-ul-fb", trainingDaysPerWeek: 4, frequencyTier: 3, sessionTypes: ["Upper", "Lower", "FullBody", "FullBody"], isDefault: true },
  { id: "4d-t3-fp-fpull-fb", trainingDaysPerWeek: 4, frequencyTier: 3, sessionTypes: ["FullPush", "FullPull", "FullBody", "FullBody"] },

  // 5 days
  { id: "5d-t1-bodypart", trainingDaysPerWeek: 5, frequencyTier: 1, sessionTypes: ["Chest", "Back", "Lower", "Shoulders", "Arms"] },
  { id: "5d-t2-l-up-upull-l-u", trainingDaysPerWeek: 5, frequencyTier: 2, sessionTypes: ["Lower", "UpperPush", "UpperPull", "Lower", "Upper"], isDefault: true },
  { id: "5d-t2-l-up-upull-fp-fpull", trainingDaysPerWeek: 5, frequencyTier: 2, sessionTypes: ["Lower", "UpperPush", "UpperPull", "FullPush", "FullPull"] },
  { id: "5d-t3-l-up-upull-full", trainingDaysPerWeek: 5, frequencyTier: 3, sessionTypes: ["Lower", "UpperPush", "UpperPull", "FullBody", "FullBody"], isDefault: true },

  // 6 days — rolling cycles (cycle length > training days/week)
  { id: "6d-t1-rolling-bp", trainingDaysPerWeek: 6, frequencyTier: 1, sessionTypes: ["Chest", "Back", "QuadsCalves", "Shoulders", "Arms", "GlutesHams"] },
  { id: "6d-t2-l-up-upull", trainingDaysPerWeek: 6, frequencyTier: 2, sessionTypes: ["Lower", "UpperPush", "UpperPull", "Lower", "UpperPush", "UpperPull"], isDefault: true },
  { id: "6d-t3-ul", trainingDaysPerWeek: 6, frequencyTier: 3, sessionTypes: ["Lower", "Upper", "Lower", "Upper", "Lower", "Upper"], isDefault: true },
  { id: "6d-t3-pushpull", trainingDaysPerWeek: 6, frequencyTier: 3, sessionTypes: ["FullPush", "FullPull", "FullPush", "FullPull", "FullPush", "FullPull"] },
];

export function splitsForDaysAndTier(
  trainingDaysPerWeek: number,
  requiredTier: FrequencyTier,
): SplitTemplate[] {
  return SPLIT_CATALOG.filter(
    (s) => s.trainingDaysPerWeek === trainingDaysPerWeek && s.frequencyTier >= requiredTier,
  );
}
