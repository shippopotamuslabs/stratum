/** Closed equipment vocabulary — keep in sync with Kotlin `GymEquipment`. */
export const GYM_EQUIPMENT = [
  "Barbell",
  "Dumbbell",
  "EzBar",
  "Kettlebell",
  "Band",
  "Bodyweight",
  "FlatBench",
  "AdjustableBench",
  "PowerRack",
  "SmithMachine",
  "CableStation",
  "CableCrossover",
  "LatPulldownMachine",
  "SeatedCableRowStation",
  "TBarRowStation",
  "LegPress",
  "HackSquat",
  "LegExtension",
  "LegCurl",
  "CalfRaiseMachine",
  "AbCrunchMachine",
  "PecDeck",
  "PreacherCurlMachine",
  "HipThrustMachine",
  "Ghd",
  "LegRaiseCaptainChair",
] as const;

export type GymEquipment = (typeof GYM_EQUIPMENT)[number];

export const GYM_EQUIPMENT_SET = new Set<string>(GYM_EQUIPMENT);
