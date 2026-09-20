import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, sep } from "node:path";
import { detectAndParseProgramTemplate } from "./formatDetector";
import { FakeJobRef } from "../../import/jobRef";
import { FakeToolStorage } from "../../import/toolStorage";
import { deterministicProgramConvert } from "./deterministicProgramConvert";
import {
  MAX_PROGRAM_TEMPLATE_FILE_BYTES,
  ProgramTemplateTooLargeError,
} from "../programTemplateLimits";

// Tests run from the compiled lib/ tree, but fixtures live under src/ (not copied by tsc).
const SRC_DIR = __dirname.replace(`${sep}lib`, `${sep}src`);
const FIXTURES = join(SRC_DIR, "fixtures");
const UID = "user-1";
const JOB_ID = "job-1";

describe("program-template formatDetector", () => {
  it("detects the Alpha Progression plan fixture", async () => {
    const bytes = readFileSync(join(FIXTURES, "alpha_progression_plan.csv"));
    const result = await detectAndParseProgramTemplate(bytes);
    assert.ok(result);
    assert.equal(result!.format, "alpha_progression");
    assert.ok(result!.template.workouts.length > 0);
  });

  it("detects the MacroFactor program fixture", async () => {
    const bytes = readFileSync(join(FIXTURES, "macro_factor_programs.xlsx"));
    const result = await detectAndParseProgramTemplate(bytes);
    assert.ok(result);
    assert.equal(result!.format, "macrofactor");
    assert.ok(result!.template.workouts.length > 0);
  });

  it("returns null for an unrecognized file (caller rejects)", async () => {
    const junk = Buffer.from("name,reps\nSquat,5\n", "utf8");
    assert.equal(await detectAndParseProgramTemplate(junk), null);
  });
});

describe("deterministicProgramConvert", () => {
  function deps(rawBytes: Buffer) {
    const storage = new FakeToolStorage();
    const rawPath = `programImports/${UID}/${JOB_ID}/raw/export`;
    storage.files.set(rawPath, rawBytes);
    const jobRef = new FakeJobRef({ rawStoragePath: rawPath });
    return { storage, jobRef };
  }

  it("converts the Alpha Progression fixture, persists template.json, marks job ready", async () => {
    const bytes = readFileSync(join(FIXTURES, "alpha_progression_plan.csv"));
    const { storage, jobRef } = deps(bytes);

    const result = await deterministicProgramConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });
    assert.ok(result);
    assert.equal(result!.format, "alpha_progression");

    const savedPaths = [...storage.files.keys()].filter((p) => p.endsWith("/template.json"));
    assert.equal(savedPaths.length, 1);
    const saved = JSON.parse(storage.files.get(savedPaths[0])!.toString("utf8"));
    assert.ok(saved.workouts.length > 0);

    assert.equal(jobRef.data.status, "ready");
    assert.equal(jobRef.data.progressPhase, "parsing");
    const latest = jobRef.data.latestConversion as { storagePath: string; format: string };
    assert.equal(latest.format, "alpha_progression");
  });

  it("converts the MacroFactor fixture", async () => {
    const bytes = readFileSync(join(FIXTURES, "macro_factor_programs.xlsx"));
    const { storage, jobRef } = deps(bytes);

    const result = await deterministicProgramConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });
    assert.ok(result);
    assert.equal(result!.format, "macrofactor");
    assert.equal(jobRef.data.status, "ready");
  });

  it("returns null for an unrecognized format so the caller rejects the job", async () => {
    const { storage, jobRef } = deps(Buffer.from("foo,bar\n1,2\n", "utf8"));
    const result = await deterministicProgramConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });
    assert.equal(result, null);
    assert.equal(jobRef.data.status, undefined);
    assert.equal(jobRef.data.latestConversion, undefined);
  });

  it("returns null when rawStoragePath is missing", async () => {
    const storage = new FakeToolStorage();
    const jobRef = new FakeJobRef({});
    const result = await deterministicProgramConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });
    assert.equal(result, null);
  });

  it("throws ProgramTemplateTooLargeError for an oversized file (caller rejects as too large)", async () => {
    const oversized = Buffer.alloc(MAX_PROGRAM_TEMPLATE_FILE_BYTES + 1, 0);
    const { storage, jobRef } = deps(oversized);
    await assert.rejects(
      () => deterministicProgramConvert({ uid: UID, jobId: JOB_ID, jobRef, storage }),
      (err: unknown) => {
        assert.ok(err instanceof ProgramTemplateTooLargeError, "expected tagged size error");
        return true;
      },
    );
    // The job is left untouched — the trigger handler is responsible for marking it rejected.
    assert.equal(jobRef.data.status, undefined);
  });
});
