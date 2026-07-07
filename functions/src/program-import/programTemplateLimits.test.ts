import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertWithinSizeLimit,
  MAX_PROGRAM_TEMPLATE_FILE_BYTES,
  ProgramTemplateTooLargeError,
} from "./programTemplateLimits";

describe("programTemplateLimits", () => {
  it("exposes a 1 MB cap", () => {
    assert.equal(MAX_PROGRAM_TEMPLATE_FILE_BYTES, 1024 * 1024);
  });

  it("accepts a file at or under the cap", () => {
    assert.doesNotThrow(() => assertWithinSizeLimit(0));
    assert.doesNotThrow(() => assertWithinSizeLimit(1));
    assert.doesNotThrow(() => assertWithinSizeLimit(MAX_PROGRAM_TEMPLATE_FILE_BYTES));
  });

  it("rejects a file over the cap with a tagged error", () => {
    assert.throws(
      () => assertWithinSizeLimit(MAX_PROGRAM_TEMPLATE_FILE_BYTES + 1),
      (err: unknown) => {
        assert.ok(err instanceof ProgramTemplateTooLargeError);
        assert.match((err as Error).message, /too large/i);
        return true;
      },
    );
  });

  it("includes the byte length in the error message", () => {
    try {
      assertWithinSizeLimit(5_000_000);
      assert.fail("expected throw");
    } catch (err) {
      assert.ok(err instanceof ProgramTemplateTooLargeError);
      assert.match((err as Error).message, /5000000/);
    }
  });
});
