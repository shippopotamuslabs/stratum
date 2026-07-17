import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { assertOwnedImportStoragePaths, parseOwnedImportPath } from "./startImport";

describe("assertOwnedImportStoragePaths", () => {
  const uid = "user-abc";
  const jobId = "job-1";

  it("accepts canonical raw + sample paths under imports/{uid}/{jobId}/", () => {
    assert.doesNotThrow(() =>
      assertOwnedImportStoragePaths(
        uid,
        `imports/${uid}/${jobId}/raw/export.csv`,
        `imports/${uid}/${jobId}/sample/export.csv`,
      ),
    );
  });

  it("rejects rawStoragePath outside the caller's prefix", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          "imports/other-user/job-1/raw/export.csv",
          `imports/${uid}/job-1/sample/export.csv`,
        ),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(String(err.message), /must start with imports\/user-abc\//);
        return true;
      },
    );
  });

  it("rejects sampleStoragePath outside the caller's prefix", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          `imports/${uid}/job-1/raw/export.csv`,
          "imports/evil/job-1/sample/export.csv",
        ),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });

  it("rejects paths that only partially match the prefix", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          `imports/${uid}X/job-1/raw.csv`,
          `imports/${uid}/job-1/sample.csv`,
        ),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });

  it("rejects path traversal", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          `imports/${uid}/job-1/raw/../../other/secret.csv`,
          `imports/${uid}/job-1/sample/export.csv`,
        ),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(String(err.message), /traversal/i);
        return true;
      },
    );
  });

  it("rejects wrong kind folder (raw path under sample/)", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          `imports/${uid}/job-1/sample/export.csv`,
          `imports/${uid}/job-1/sample/export.csv`,
        ),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(String(err.message), /\/raw\//);
        return true;
      },
    );
  });

  it("rejects mismatched jobId between raw and sample", () => {
    assert.throws(
      () =>
        assertOwnedImportStoragePaths(
          uid,
          `imports/${uid}/job-1/raw/export.csv`,
          `imports/${uid}/job-2/sample/export.csv`,
        ),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(String(err.message), /same jobId/);
        return true;
      },
    );
  });

  it("rejects absolute paths", () => {
    assert.throws(
      () =>
        parseOwnedImportPath(
          uid,
          `/imports/${uid}/job-1/raw/export.csv`,
          "raw",
        ),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });

  it("rejects invalid jobId characters", () => {
    assert.throws(
      () =>
        parseOwnedImportPath(
          uid,
          `imports/${uid}/job id/raw/export.csv`,
          "raw",
        ),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });
});
