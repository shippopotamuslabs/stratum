import { logger } from "firebase-functions";

/**
 * Program generation v2 diagnostics (OS-7 v2).
 *
 * The v1 tool-phase diagnostics (logProgramGenerationToolTurn/ToolPhaseComplete/ToolCall,
 * summarizeToolResultPayload, countIssueSeverities, draftWorkoutCount) are deleted with the tool
 * phase. [logProgramGenerationMetricAttempt] is kept for Phase 2 final validation; the new
 * pipeline-specific loggers cover split-planner, per-workout builder, and total pipeline outcomes.
 */

/** Logs a Phase 2 metric-validation attempt (kept from v1; the final gate still uses validators). */
export function logProgramGenerationMetricAttempt(
  sessionId: string,
  attempt: number,
  pass: boolean,
  issueCount: number,
  issueCodes: string[],
): void {
  const payload = {
    sessionId,
    attempt,
    pass,
    issueCount,
    issueCodes: issueCodes.slice(0, 8),
  };
  if (pass) {
    logger.info("programGeneration.metricValidation", payload);
  } else {
    logger.warn("programGeneration.metricValidation", payload);
  }
}

/** Logs the split-planner outcome (used LLM vs deterministic fallback, valid/invalid). */
export function logSplitPlannerResult(
  sessionId: string,
  usedLlm: boolean,
  valid: boolean,
  fallback: boolean,
): void {
  logger.info("programGeneration.splitPlanner", { sessionId, usedLlm, valid, fallback });
}

/** Logs a per-workout builder outcome (latency, whether the deterministic fallback was used). */
export function logWorkoutBuilderResult(
  sessionId: string,
  dayIndex: number,
  latencyMs: number,
  usedFallback: boolean,
): void {
  logger.info("programGeneration.workoutBuilder", {
    sessionId,
    dayIndex,
    latencyMs,
    usedFallback,
  });
}

/** Logs the total pipeline duration + how many workouts fell through to the fallback. */
export function logPipelineTotal(
  sessionId: string,
  phaseMs: { splitPlannerMs: number; buildersMs: number; integrateMs: number; totalMs: number },
  usedFallbackCount: number,
): void {
  logger.info("programGeneration.pipelineTotal", { sessionId, ...phaseMs, usedFallbackCount });
}