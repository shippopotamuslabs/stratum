/**
 * Optional LLM review pass on the finished program: swap liftIds only when clearly better.
 */
import type OpenAI from "openai";
import { buildProgramGenerationChatCompletionExtras } from "../openrouterSession";
import type { GenerationContext, LiftContext } from "./programPayloadContext";
import type { BuiltProgram } from "./slotBasedProgramBuilder";
import type { SelectedSlotLift } from "./liftSlotSelector";
import { scoreLift, compareLiftCandidates } from "./liftPriority";
import { liftCatalogQualityAdjustment } from "./liftCatalogQuality";
import {
  buildLiftSwapInput,
  LIFT_SWAP_MAX_PER_WORKOUT,
  LIFT_SWAP_PROMPT_VERSION,
  LIFT_SWAP_SYSTEM_PROMPT,
  liftSwapOutputSchema,
  type LiftSwapCandidatePayload,
  type LiftSwapReviewPayload,
} from "./liftSwapPrompt";
import type { ProgramDraft } from "./programValidation";

export const PROGRAM_LIFT_SWAP_ENABLED = process.env.PROGRAM_LIFT_SWAP_ENABLED !== "false";

/** Pattern-matched alternatives sent to the reviewer per exercise. */
export const LIFT_SWAP_CANDIDATES_PER_LIFT = 10;

export type ChatCompletionLike = Pick<OpenAI, "chat">;

export interface LiftSwapCandidateMap {
  /** workoutPosition -> liftPosition -> candidate liftIds */
  byWorkout: Map<number, Map<number, string[]>>;
}

export interface SwapRequest {
  workoutPosition: number;
  liftPosition: number;
  newLiftId: string;
}

function specializedMuscles(context: GenerationContext): string[] {
  return Object.entries(context.specialization)
    .filter(([, level]) => level === "Specialize")
    .map(([muscle]) => muscle);
}

function liftToCandidatePayload(lift: LiftContext): LiftSwapCandidatePayload {
  return {
    liftId: lift.id,
    name: lift.name || lift.id,
    movementPattern: lift.movementPattern,
    primaryMuscles: lift.primaryMuscles,
  };
}

function swapCandidateScore(lift: LiftContext, context: GenerationContext): number {
  return scoreLift(lift, context) + liftCatalogQualityAdjustment(lift);
}

function matchesPattern(lift: LiftContext, pattern: string): boolean {
  return lift.movementPattern === pattern || lift.movementPatterns.includes(pattern);
}

/** Enabled lifts that could replace [currentLiftId] in the same slot (pattern + primary overlap). */
export function alternativesForLift(
  context: GenerationContext,
  currentLiftId: string,
  seedCandidateIds: string[],
): string[] {
  const current = context.liftsById.get(currentLiftId);
  if (!current) return [...new Set(seedCandidateIds)];

  const primaryMuscles = new Set(current.primaryMuscles);
  const pattern = current.movementPattern;

  const ranked = [...context.liftsById.values()]
    .filter((lift) => context.enabledLiftIds.has(lift.id))
    .filter((lift) => lift.id !== currentLiftId)
    .filter((lift) => matchesPattern(lift, pattern))
    .filter((lift) => lift.primaryMuscles.some((m) => primaryMuscles.has(m)))
    .sort((a, b) => {
      const scoreDiff = swapCandidateScore(b, context) - swapCandidateScore(a, context);
      if (scoreDiff !== 0) return scoreDiff;
      return compareLiftCandidates(
        {
          liftId: a.id,
          name: a.name,
          pattern: a.movementPattern,
          priority: swapCandidateScore(a, context),
          priorityTier: a.proven ? "proven" : "available",
          isCompound: a.movementPatterns.length > 1,
          mainstream: a.mainstream === true,
          primaryMuscles: a.primaryMuscles,
          secondaryMuscles: a.secondaryMuscles,
        },
        {
          liftId: b.id,
          name: b.name,
          pattern: b.movementPattern,
          priority: swapCandidateScore(b, context),
          priorityTier: b.proven ? "proven" : "available",
          isCompound: b.movementPatterns.length > 1,
          mainstream: b.mainstream === true,
          primaryMuscles: b.primaryMuscles,
          secondaryMuscles: b.secondaryMuscles,
        },
      );
    })
    .map((lift) => lift.id);

  const merged: string[] = [];
  const seen = new Set<string>();
  for (const id of [...seedCandidateIds, ...ranked, currentLiftId]) {
    if (seen.has(id)) continue;
    if (!context.enabledLiftIds.has(id)) continue;
    seen.add(id);
    merged.push(id);
  }
  merged.sort((a, b) => {
    const liftA = context.liftsById.get(a);
    const liftB = context.liftsById.get(b);
    if (!liftA || !liftB) return 0;
    return swapCandidateScore(liftB, context) - swapCandidateScore(liftA, context);
  });
  return merged.slice(0, LIFT_SWAP_CANDIDATES_PER_LIFT);
}

export function buildSwapCandidateMap(
  program: ProgramDraft,
  context: GenerationContext,
  selectionsBySession: SelectedSlotLift[][],
): LiftSwapCandidateMap {
  const byWorkout = new Map<number, Map<number, string[]>>();

  program.workouts.forEach((workout, workoutPosition) => {
    const selectionSeeds = selectionsBySession[workoutPosition] ?? [];
    const liftMap = new Map<number, string[]>();

    (workout.workoutLifts ?? []).forEach((lift, liftPosition) => {
      const liftId = lift.liftId ?? "";
      const seedIds = selectionSeeds[liftPosition]?.candidates.map((c) => c.id) ?? [];
      liftMap.set(liftPosition, alternativesForLift(context, liftId, seedIds));
    });

    byWorkout.set(workoutPosition, liftMap);
  });

  return { byWorkout };
}

export function buildLiftSwapReviewPayload(
  program: ProgramDraft,
  context: GenerationContext,
  candidateMap: LiftSwapCandidateMap,
): LiftSwapReviewPayload {
  return {
    promptVersion: LIFT_SWAP_PROMPT_VERSION,
    intent: {
      trainingAge: context.trainingAge,
      sessionLengthMinutes: context.sessionLengthMinutes,
      trainingDaysPerWeek: context.trainingDaysPerWeek,
      cycleLength: program.cycleLength ?? program.workouts.length,
      specialization: context.specialization,
      specializedMuscles: specializedMuscles(context),
    },
    program: {
      cycleLength: program.cycleLength ?? program.workouts.length,
      workouts: program.workouts.map((workout, workoutPosition) => ({
        position: workout.position ?? workoutPosition,
        name: workout.name ?? `Workout ${workoutPosition + 1}`,
        workoutLifts: (workout.workoutLifts ?? []).map((lift, liftPosition) => {
          const liftId = lift.liftId ?? "";
          const meta = context.liftsById.get(liftId);
          const sets = lift.workoutLiftSets ?? [];
          const repMins = sets
            .map((s) => (s && typeof s === "object" ? (s as { repMin?: unknown }).repMin : undefined))
            .filter((v): v is number => typeof v === "number");
          const repMaxs = sets
            .map((s) => (s && typeof s === "object" ? (s as { repMax?: unknown }).repMax : undefined))
            .filter((v): v is number => typeof v === "number");
          const candidateIds = candidateMap.byWorkout.get(workoutPosition)?.get(liftPosition) ?? [];

          return {
            position: liftPosition,
            liftId,
            name: meta?.name ?? liftId,
            movementPattern: meta?.movementPattern ?? "Unknown",
            primaryMuscles: meta?.primaryMuscles ?? [],
            secondaryMuscles: meta?.secondaryMuscles ?? [],
            setCount: sets.length,
            repMin: repMins.length > 0 ? Math.min(...repMins) : null,
            repMax: repMaxs.length > 0 ? Math.max(...repMaxs) : null,
            candidates: candidateIds
              .map((id) => context.liftsById.get(id))
              .filter((c): c is LiftContext => c != null)
              .map(liftToCandidatePayload),
          };
        }),
      })),
    },
  };
}

function parseSwapResponse(content: string): SwapRequest[] {
  const parsed = JSON.parse(content) as { swaps?: unknown };
  if (!Array.isArray(parsed.swaps)) return [];
  const out: SwapRequest[] = [];
  for (const row of parsed.swaps) {
    if (!row || typeof row !== "object") continue;
    const r = row as Record<string, unknown>;
    if (
      typeof r.workoutPosition === "number" &&
      typeof r.liftPosition === "number" &&
      typeof r.newLiftId === "string"
    ) {
      out.push({
        workoutPosition: r.workoutPosition,
        liftPosition: r.liftPosition,
        newLiftId: r.newLiftId,
      });
    }
  }
  return out;
}

export function validateSwaps(
  swaps: SwapRequest[],
  program: ProgramDraft,
  candidateMap: LiftSwapCandidateMap,
): SwapRequest[] {
  const valid: SwapRequest[] = [];
  const perWorkout = new Map<number, number>();
  for (const swap of swaps) {
    if (swap.workoutPosition < 0 || swap.workoutPosition >= program.workouts.length) continue;
    const workout = program.workouts[swap.workoutPosition]!;
    const lifts = workout.workoutLifts ?? [];
    if (swap.liftPosition < 0 || swap.liftPosition >= lifts.length) continue;
    const currentLiftId = lifts[swap.liftPosition]?.liftId;
    if (swap.newLiftId === currentLiftId) continue;
    const candidates = candidateMap.byWorkout.get(swap.workoutPosition)?.get(swap.liftPosition);
    if (!candidates?.includes(swap.newLiftId)) continue;
    const count = perWorkout.get(swap.workoutPosition) ?? 0;
    if (count >= LIFT_SWAP_MAX_PER_WORKOUT) continue;
    perWorkout.set(swap.workoutPosition, count + 1);
    valid.push(swap);
  }
  return valid;
}

export function applySwaps<T extends ProgramDraft>(program: T, swaps: SwapRequest[]): T {
  if (swaps.length === 0) return program;
  const workouts = program.workouts.map((w) => ({
    ...w,
    workoutLifts: (w.workoutLifts ?? []).map((lift) => ({ ...lift })),
  }));
  for (const swap of swaps) {
    const lift = workouts[swap.workoutPosition]?.workoutLifts?.[swap.liftPosition];
    if (lift) lift.liftId = swap.newLiftId;
  }
  return { ...program, workouts };
}

/** @deprecated Use [buildSwapCandidateMap] — kept for existing tests. */
export function buildCandidateMap(
  selectionsBySession: SelectedSlotLift[][],
): LiftSwapCandidateMap {
  const byWorkout = new Map<number, Map<number, string[]>>();
  selectionsBySession.forEach((selections, workoutPosition) => {
    const liftMap = new Map<number, string[]>();
    selections.forEach((sel, liftPosition) => {
      liftMap.set(liftPosition, sel.candidates.map((c) => c.id));
    });
    byWorkout.set(workoutPosition, liftMap);
  });
  return { byWorkout };
}

export async function maybeSwapLifts(
  client: ChatCompletionLike,
  model: string,
  program: ProgramDraft,
  context: GenerationContext,
  selectionsBySession: SelectedSlotLift[][],
  sessionId: string,
): Promise<ProgramDraft> {
  if (!PROGRAM_LIFT_SWAP_ENABLED) return program;

  const candidateMap = buildSwapCandidateMap(program, context, selectionsBySession);
  const swapInput = buildLiftSwapInput(
    buildLiftSwapReviewPayload(program, context, candidateMap),
  );

  const extras = buildProgramGenerationChatCompletionExtras(sessionId);
  const response = await client.chat.completions.create({
    model,
    messages: [
      { role: "system", content: LIFT_SWAP_SYSTEM_PROMPT },
      { role: "user", content: JSON.stringify(swapInput) },
    ],
    response_format: { type: "json_schema", json_schema: liftSwapOutputSchema() },
    temperature: 0.2,
    ...extras,
  });

  const content = response.choices[0]?.message?.content;
  if (!content) return program;

  let swaps: SwapRequest[] = [];
  try {
    swaps = validateSwaps(parseSwapResponse(content), program, candidateMap);
  } catch {
    return program;
  }
  return applySwaps(program, swaps);
}

/** @deprecated Use [maybeSwapLifts] with context — kept for type re-exports in tests. */
export type { BuiltProgram };
