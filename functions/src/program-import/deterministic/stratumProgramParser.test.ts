import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, sep } from "node:path";
import { parseStratumProgramExport } from "./stratumProgramParser";
import { detectAndParseProgramTemplate } from "./formatDetector";

const SRC_DIR = __dirname.replace(`${sep}lib`, `${sep}src`);
const FIXTURES = join(SRC_DIR, "fixtures");

const FIX_STRATUM_PROGRAMS = readFileSync(join(FIXTURES, "stratum_programs_v1.json"), "utf8");

describe("stratumProgramParser", () => {
  it("IE-S3-004 Stratum program fixture detects as stratum", () => {
    const result = detectAndParseProgramTemplate(Buffer.from(FIX_STRATUM_PROGRAMS, "utf8"));
    assert.ok(result);
    assert.equal(result!.format, "stratum");
  });

  it("IE-S3-005 Multi-program parse returns both templates", () => {
    const parsed = parseStratumProgramExport(FIX_STRATUM_PROGRAMS);
    assert.equal(parsed.programs.length, 2);
    assert.deepEqual(
      parsed.programs.map((p) => p.id),
      ["prog-a", "prog-b"],
    );
    for (const p of parsed.programs) {
      assert.ok(p.template.workouts.length >= 1);
    }
  });

  it("IE-S3-008 Alpha Progression program CSV still detects as alpha_progression", () => {
    const bytes = readFileSync(join(FIXTURES, "alpha_progression_plan.csv"));
    const result = detectAndParseProgramTemplate(bytes);
    assert.ok(result);
    assert.equal(result!.format, "alpha_progression");
  });
});
