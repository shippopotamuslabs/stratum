import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  executeConversionScript,
  validateScriptSafety,
} from "./pythonSandbox";
import { SAMPLE_CONVERTER_SCRIPT } from "./testFixtures";

describe("validateScriptSafety", () => {
  it("blocks dangerous tokens", () => {
    assert.throws(
      () => validateScriptSafety("import os\n" + SAMPLE_CONVERTER_SCRIPT),
      /Blocked token/,
    );
    assert.throws(() => validateScriptSafety("open('/etc/passwd')"), /Blocked token/);
    assert.throws(() => validateScriptSafety("__import__('os')"), /Blocked token/);
  });

  it("allows safe converter scripts", () => {
    assert.doesNotThrow(() => validateScriptSafety(SAMPLE_CONVERTER_SCRIPT));
  });
});

describe("executeConversionScript", () => {
  it("runs a valid converter and returns normalized JSON", () => {
    const result = executeConversionScript(SAMPLE_CONVERTER_SCRIPT, "ignored input");
    assert.equal(result.rowCount, 1);
    assert.equal(result.sessionCount, 1);
    const parsed = JSON.parse(result.outputJson) as { sets: unknown[]; sessions: unknown[] };
    assert.equal(parsed.sets.length, 1);
    assert.equal(parsed.sessions.length, 1);
  });

  it("fails when convert is not defined", () => {
    assert.throws(
      () => executeConversionScript("x = 1", "input"),
      /Script must define convert/,
    );
  });

  it("fails when script raises at runtime", () => {
    const script = `
def convert(raw_input: str) -> dict:
    raise ValueError("bad data")
`;
    assert.throws(() => executeConversionScript(script, "input"), /bad data/);
  });
});
