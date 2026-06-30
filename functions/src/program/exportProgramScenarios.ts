/**
 * Batch-generates programs for configured scenarios and exports review CSVs.
 * Used by scripts/export-program-scenarios.mjs for local eyeball validation.
 */
import fs from "node:fs";
import path from "node:path";
import type { GenerationContext } from "./programPayloadContext";
import { parseGenerationContext, toNormalizationRequest } from "./programPayloadContext";
import { generateProgramPipeline } from "./generateProgramPipeline";
import {
  computeWorkoutMuscleVolume,
  validateProgramMetrics,
  type ProgramDraft,
  type ProgramLiftDraft,
} from "./programValidation";

export interface ScenarioDefaults {
  trainingAge?: string;
  sessionLengthMinutes?: number;
  lastTrained?: string | null;
  liftCatalog?: "commercial" | "test";
}

export interface ScenarioDefinition {
  id: string;
  label?: string;
  trainingDaysPerWeek: number;
  specialization?: Record<string, string>;
  trainingAge?: string;
  sessionLengthMinutes?: number;
  lastTrained?: string | null;
  weekdays?: string[];
}

export interface ScenarioBatchFile {
  defaults?: ScenarioDefaults;
  scenarios: ScenarioDefinition[];
}

export interface LiftCatalogEntry {
  id: string;
  name: string;
  movementPattern: string;
  movementPatterns: string[];
  primaryMuscles: string[];
  secondaryMuscles: string[];
}

export interface ExportProgramsOptions {
  repoRoot: string;
  batch: ScenarioBatchFile;
  outputPath: string;
  summaryPath?: string;
}

export interface ExportProgramsResult {
  scenarioCount: number;
  detailRowCount: number;
  summaryRowCount: number;
  outputPath: string;
  summaryPath: string;
  markdownPath: string;
}

const WEEKDAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

const TEST_LIFT_CATALOG: LiftCatalogEntry[] = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", movementPatterns: ["HorizontalPress"], primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", movementPatterns: ["HorizontalPress"], primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "inc-press", name: "Incline Press", movementPattern: "InclinePress", movementPatterns: ["InclinePress"], primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", movementPatterns: ["HorizontalPull"], primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", movementPatterns: ["HorizontalPull"], primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "pullup", name: "Pullup", movementPattern: "VerticalPull", movementPatterns: ["VerticalPull"], primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", movementPatterns: ["SquatPattern"], primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "SquatPattern", movementPatterns: ["SquatPattern"], primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "leg-ext", name: "Leg Extension", movementPattern: "KneeExtension", movementPatterns: ["KneeExtension"], primaryMuscles: ["Quad"], secondaryMuscles: [] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", movementPatterns: ["HipHinge"], primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", movementPatterns: ["HipHinge"], primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "leg-curl", name: "Leg Curl", movementPattern: "KneeFlexion", movementPatterns: ["KneeFlexion"], primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "PlantarFlexion", movementPatterns: ["PlantarFlexion"], primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "ShoulderAbduction", movementPatterns: ["ShoulderAbduction"], primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "ohp", name: "Overhead Press", movementPattern: "VerticalPress", movementPatterns: ["VerticalPress"], primaryMuscles: ["AnteriorDeltoid"], secondaryMuscles: ["Tricep"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", movementPatterns: ["ElbowFlexion"], primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", movementPatterns: ["ElbowExtension"], primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  { id: "hip-thrust", name: "Hip Thrust", movementPattern: "HipExtension", movementPatterns: ["HipExtension"], primaryMuscles: ["Glute"], secondaryMuscles: ["Hamstring"] },
];

const DEFAULT_LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Glute", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "PosteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "AnteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
];

function noopClient() {
  return {
    chat: {
      completions: {
        create: async () => ({ choices: [{ message: { content: '{"swaps":[]}' } }] }),
      },
    },
  };
}

export function loadScenarioBatchFile(filePath: string): ScenarioBatchFile {
  const raw = fs.readFileSync(filePath, "utf8");
  return JSON.parse(raw) as ScenarioBatchFile;
}

export function loadLiftCatalog(repoRoot: string, preset: "commercial" | "test"): LiftCatalogEntry[] {
  if (preset === "test") return TEST_LIFT_CATALOG;
  const seedPath = path.join(repoRoot, "core/data/src/main/assets/lifts/seed-lifts.json");
  const seed = JSON.parse(fs.readFileSync(seedPath, "utf8")) as Array<{
    id: string;
    name: string;
    movementPatterns: string[];
    primaryMuscles: string[];
    secondaryMuscles?: string[];
    presetTags?: string[];
  }>;
  return seed
    .filter((l) => (l.presetTags ?? []).includes("CommercialGym"))
    .map((l) => ({
      id: l.id,
      name: l.name,
      movementPattern: l.movementPatterns[0] ?? "Unknown",
      movementPatterns: l.movementPatterns,
      primaryMuscles: l.primaryMuscles,
      secondaryMuscles: l.secondaryMuscles ?? [],
    }));
}

function weekdaysForDays(count: number, override?: string[]): string[] {
  if (override != null && override.length >= count) return override.slice(0, count);
  return WEEKDAY_NAMES.slice(0, count);
}

function specializationLabel(spec: Record<string, string>): string {
  const parts = Object.entries(spec)
    .filter(([, level]) => level === "Specialize")
    .map(([muscle]) => muscle);
  return parts.length === 0 ? "maintenance" : parts.join("+");
}

export function buildScenarioPayload(
  scenario: ScenarioDefinition,
  defaults: ScenarioDefaults,
  lifts: LiftCatalogEntry[],
): string {
  const days = scenario.trainingDaysPerWeek;
  return JSON.stringify({
    profile: {
      trainingAge: scenario.trainingAge ?? defaults.trainingAge ?? "Advanced",
      sessionLengthMinutes: scenario.sessionLengthMinutes ?? defaults.sessionLengthMinutes ?? 75,
      lastTrained: scenario.lastTrained ?? defaults.lastTrained ?? "WithinLastMonth",
      weekdays: weekdaysForDays(days, scenario.weekdays).join(","),
      trainingDaysPerWeek: days,
    },
    specialization: scenario.specialization ?? {},
    provenLifts: lifts,
    availableLifts: [],
    literatureVolumeLandmarks: DEFAULT_LANDMARKS,
  });
}

export function csvEscape(value: string | number | boolean | null | undefined): string {
  if (value == null) return "";
  const text = String(value);
  if (/[",\n\r]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
}

export function rowsToCsv(rows: string[][]): string {
  return rows.map((row) => row.map(csvEscape).join(",")).join("\n") + "\n";
}

/** Parses a simple CSV string (supports quoted fields). */
export function parseCsv(content: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < content.length; i++) {
    const ch = content[i]!;
    if (inQuotes) {
      if (ch === "\"" && content[i + 1] === "\"") {
        field += "\"";
        i++;
      } else if (ch === "\"") {
        inQuotes = false;
      } else {
        field += ch;
      }
      continue;
    }
    if (ch === "\"") {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && content[i + 1] === "\n") i++;
      row.push(field);
      if (row.some((cell) => cell.length > 0)) rows.push(row);
      row = [];
      field = "";
    } else {
      field += ch;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    if (row.some((cell) => cell.length > 0)) rows.push(row);
  }
  return rows;
}

export interface ProgramMarkdownLiftRow {
  liftName: string;
  sets: number;
  repMin: number;
  repMax: number;
}

interface ProgramMarkdownLiftRowWithOrder extends ProgramMarkdownLiftRow {
  _order: number;
}

export interface ProgramMarkdownWorkout {
  workoutName: string;
  lifts: ProgramMarkdownLiftRow[];
}

export interface ProgramMarkdownSection {
  programName: string;
  workouts: ProgramMarkdownWorkout[];
}

function formatRepRange(repMin: number, repMax: number): string {
  if (repMin === repMax) return String(repMin);
  return `${repMin}–${repMax}`;
}

function formatLiftLine(lift: ProgramMarkdownLiftRow): string {
  return `${lift.liftName} — ${lift.sets} × ${formatRepRange(lift.repMin, lift.repMax)}`;
}

/** Groups detail CSV rows into program → workout → lift structure. */
export function detailCsvRowsToSections(rows: string[][]): ProgramMarkdownSection[] {
  if (rows.length <= 1) return [];
  const header = rows[0]!;
  const col = (name: string) => header.indexOf(name);
  const programWorkouts = new Map<string, Map<string, ProgramMarkdownLiftRowWithOrder[]>>();

  for (const row of rows.slice(1)) {
    const programName = row[col("scenario_label")] ?? row[col("scenario_id")] ?? "Program";
    const workoutName = row[col("workout")] ?? "Workout";
    const liftName = row[col("lift_name")] ?? row[col("lift_id")] ?? "Lift";
    const sets = Number(row[col("sets")] ?? 0);
    const repMin = Number(row[col("rep_min")] ?? 0);
    const repMax = Number(row[col("rep_max")] ?? 0);
    const liftOrder = Number(row[col("lift_order")] ?? 0);

    if (!programWorkouts.has(programName)) {
      programWorkouts.set(programName, new Map());
    }
    const workouts = programWorkouts.get(programName)!;
    if (!workouts.has(workoutName)) {
      workouts.set(workoutName, []);
    }
    workouts.get(workoutName)!.push({ liftName, sets, repMin, repMax, _order: liftOrder });
  }

  const sections: ProgramMarkdownSection[] = [];
  for (const [programName, workouts] of programWorkouts) {
    const workoutList: ProgramMarkdownWorkout[] = [];
    for (const [workoutName, lifts] of workouts) {
      workoutList.push({
        workoutName,
        lifts: [...lifts]
          .sort((a, b) => a._order - b._order)
          .map(({ _order: _ignored, ...lift }) => lift),
      });
    }
    sections.push({ programName, workouts: workoutList });
  }
  return sections;
}

/** Converts detail CSV text to readable markdown. */
export function detailCsvToMarkdown(csvContent: string): string {
  const sections = detailCsvRowsToSections(parseCsv(csvContent.trim()));
  const blocks: string[] = [];

  for (const section of sections) {
    blocks.push(`# ${section.programName}`, "");
    for (const workout of section.workouts) {
      blocks.push(`## ${workout.workoutName}`, "");
      for (const lift of workout.lifts) {
        blocks.push(formatLiftLine(lift));
      }
      blocks.push("");
    }
  }

  return blocks.join("\n").trimEnd() + "\n";
}

export function writeProgramMarkdownFromDetailCsv(csvPath: string, markdownPath: string): void {
  const csvContent = fs.readFileSync(csvPath, "utf8");
  fs.mkdirSync(path.dirname(markdownPath), { recursive: true });
  fs.writeFileSync(markdownPath, detailCsvToMarkdown(csvContent), "utf8");
}

function repRangeForLift(lift: ProgramLiftDraft): { repMin: number | ""; repMax: number | ""; rirGoal: number | "" } {
  const sets = lift.workoutLiftSets ?? [];
  if (sets.length === 0) return { repMin: "", repMax: "", rirGoal: "" };
  const first = sets[0] as { repMin?: number; repMax?: number; rirGoal?: number };
  return {
    repMin: first.repMin ?? "",
    repMax: first.repMax ?? "",
    rirGoal: first.rirGoal ?? "",
  };
}

function primarySetsInWorkout(
  workout: ProgramDraft["workouts"][number],
  context: GenerationContext,
): Record<string, number> {
  const totals: Record<string, number> = {};
  for (const lift of workout.workoutLifts ?? []) {
    const meta = context.liftsById.get(lift.liftId ?? "");
    const count = lift.workoutLiftSets?.length ?? 0;
    if (!meta || count === 0) continue;
    for (const muscle of meta.primaryMuscles) {
      totals[muscle] = (totals[muscle] ?? 0) + count;
    }
  }
  return totals;
}

export async function exportProgramsToCsv(options: ExportProgramsOptions): Promise<ExportProgramsResult> {
  const defaults = options.batch.defaults ?? {};
  const catalogPreset = defaults.liftCatalog ?? "commercial";
  const lifts = loadLiftCatalog(options.repoRoot, catalogPreset);

  const detailHeader = [
    "scenario_id",
    "scenario_label",
    "training_days",
    "session_minutes",
    "training_age",
    "specialization",
    "workout",
    "workout_position",
    "lift_order",
    "lift_id",
    "lift_name",
    "movement_pattern",
    "primary_muscles",
    "sets",
    "rep_min",
    "rep_max",
    "rir_goal",
  ];

  const summaryHeader = [
    "scenario_id",
    "scenario_label",
    "training_days",
    "session_minutes",
    "specialization",
    "workout",
    "workout_position",
    "total_sets",
    "est_minutes",
    "validation_pass",
    "validation_issues",
    "weekly_bicep",
    "weekly_tricep",
    "weekly_chest",
    "weekly_back",
    "session_bicep_primary",
    "session_tricep_primary",
    "session_bicep_fractional",
    "session_tricep_fractional",
    "session_back_primary",
    "session_chest_primary",
  ];

  const detailRows: string[][] = [detailHeader];
  const summaryRows: string[][] = [summaryHeader];

  for (const scenario of options.batch.scenarios) {
    const payload = buildScenarioPayload(scenario, defaults, lifts);
    const context = parseGenerationContext(payload);
    const program = await generateProgramPipeline(
      noopClient() as never,
      "local-export",
      payload,
      `export-${scenario.id}`,
      toNormalizationRequest(context),
      { skipLiftSwap: true, skipFinalValidation: true },
    ) as ProgramDraft;

    const report = validateProgramMetrics(program, context);
    const weekly = report.summary.weeklyMuscleVolume;
    const specLabel = specializationLabel(scenario.specialization ?? {});
    const label = scenario.label ?? scenario.id;

    for (const workout of program.workouts) {
      const workoutIndex = workout.position ?? program.workouts.indexOf(workout);
      const primary = primarySetsInWorkout(workout, context);
      const fractional = computeWorkoutMuscleVolume(workout, context);
      const totalSets = (workout.workoutLifts ?? []).reduce(
        (sum, lift) => sum + (lift.workoutLiftSets?.length ?? 0),
        0,
      );
      const estMinutes = report.summary.estimatedWorkoutMinutes[workoutIndex] ?? totalSets * 3.5;

      summaryRows.push([
        scenario.id,
        label,
        String(scenario.trainingDaysPerWeek),
        String(scenario.sessionLengthMinutes ?? defaults.sessionLengthMinutes ?? 75),
        specLabel,
        workout.name ?? `Workout ${workoutIndex + 1}`,
        String(workoutIndex),
        String(totalSets),
        String(Math.round(estMinutes)),
        String(report.pass),
        report.issues.map((i) => i.code).join(";"),
        String(Math.round((weekly.Bicep ?? 0) * 10) / 10),
        String(Math.round((weekly.Tricep ?? 0) * 10) / 10),
        String(Math.round((weekly.Chest ?? 0) * 10) / 10),
        String(Math.round((weekly.Back ?? 0) * 10) / 10),
        String(primary.Bicep ?? 0),
        String(primary.Tricep ?? 0),
        String(Math.round((fractional.Bicep ?? 0) * 10) / 10),
        String(Math.round((fractional.Tricep ?? 0) * 10) / 10),
        String(primary.Back ?? 0),
        String(primary.Chest ?? 0),
      ]);

      (workout.workoutLifts ?? []).forEach((lift, liftOrder) => {
        const meta = context.liftsById.get(lift.liftId ?? "");
        const reps = repRangeForLift(lift);
        detailRows.push([
          scenario.id,
          label,
          String(scenario.trainingDaysPerWeek),
          String(scenario.sessionLengthMinutes ?? defaults.sessionLengthMinutes ?? 75),
          scenario.trainingAge ?? defaults.trainingAge ?? "Advanced",
          specLabel,
          workout.name ?? `Workout ${workoutIndex + 1}`,
          String(workoutIndex),
          String(liftOrder + 1),
          lift.liftId ?? "",
          meta?.name ?? lift.liftId ?? "",
          meta?.movementPattern ?? "",
          (meta?.primaryMuscles ?? []).join("|"),
          String(lift.workoutLiftSets?.length ?? 0),
          String(reps.repMin),
          String(reps.repMax),
          String(reps.rirGoal),
        ]);
      });
    }
  }

  fs.mkdirSync(path.dirname(options.outputPath), { recursive: true });
  fs.writeFileSync(options.outputPath, rowsToCsv(detailRows), "utf8");

  const summaryPath = options.summaryPath ?? options.outputPath.replace(/\.csv$/i, "-summary.csv");
  fs.writeFileSync(summaryPath, rowsToCsv(summaryRows), "utf8");

  const markdownPath = options.outputPath.replace(/\.csv$/i, ".md");
  writeProgramMarkdownFromDetailCsv(options.outputPath, markdownPath);

  return {
    scenarioCount: options.batch.scenarios.length,
    detailRowCount: detailRows.length - 1,
    summaryRowCount: summaryRows.length - 1,
    outputPath: options.outputPath,
    summaryPath,
    markdownPath,
  };
}
