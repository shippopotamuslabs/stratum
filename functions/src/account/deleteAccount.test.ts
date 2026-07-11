import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  parseDeleteAccountPhase,
  userFirestorePath,
  userStoragePrefixes,
  USER_STORAGE_PREFIXES,
  deleteUserCloudAccount,
} from "./deleteAccount";

describe("deleteAccount helpers", () => {
  it("userFirestorePath is users/{uid}", () => {
    assert.equal(userFirestorePath("abc"), "users/abc");
  });

  it("userStoragePrefixes covers import roots", () => {
    assert.deepEqual(USER_STORAGE_PREFIXES, ["imports", "programImports"]);
    assert.deepEqual(userStoragePrefixes("uid-1"), [
      "imports/uid-1/",
      "programImports/uid-1/",
    ]);
  });

  it("parseDeleteAccountPhase accepts known phases and defaults absent to all", () => {
    assert.equal(parseDeleteAccountPhase("data"), "data");
    assert.equal(parseDeleteAccountPhase("auth"), "auth");
    assert.equal(parseDeleteAccountPhase("all"), "all");
    assert.equal(parseDeleteAccountPhase(undefined), "all");
    assert.equal(parseDeleteAccountPhase(null), "all");
  });

  it("parseDeleteAccountPhase rejects unknown phases", () => {
    assert.throws(() => parseDeleteAccountPhase("nope"), /Invalid deleteAccount phase/);
    assert.throws(() => parseDeleteAccountPhase("Data"), /Invalid deleteAccount phase/);
    assert.throws(() => parseDeleteAccountPhase(1), /Invalid deleteAccount phase/);
  });
});

describe("deleteUserCloudAccount orchestration", () => {
  it("phase data wipes once and skips auth", async () => {
    const calls: string[] = [];
    await deleteUserCloudAccount("uid-1", "data", {
      wipeData: async () => {
        calls.push("wipe");
      },
      deleteAuth: async () => {
        calls.push("auth");
      },
    });
    assert.deepEqual(calls, ["wipe"]);
  });

  it("phase auth settles data then deletes auth", async () => {
    const calls: string[] = [];
    await deleteUserCloudAccount("uid-1", "auth", {
      wipeData: async () => {
        calls.push("wipe");
      },
      deleteAuth: async () => {
        calls.push("auth");
      },
    });
    assert.deepEqual(calls, ["wipe", "auth"]);
  });

  it("phase all wipes then settle-wipes then auth", async () => {
    const calls: string[] = [];
    await deleteUserCloudAccount("uid-1", "all", {
      wipeData: async () => {
        calls.push("wipe");
      },
      deleteAuth: async () => {
        calls.push("auth");
      },
    });
    assert.deepEqual(calls, ["wipe", "wipe", "auth"]);
  });
});
