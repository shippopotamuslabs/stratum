import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  MEMBERSHIP_REQUIRED_MESSAGE,
  entitlementDocPath,
  isEntitledFromDocs,
  isUserEntitled,
  assertUserEntitled,
  resetEntitlementFirestoreGetter,
  setEntitlementFirestoreGetter,
  type EntitlementFirestore,
} from "./assertEntitled";

const NOW = Date.parse("2026-08-14T20:00:00.000Z");
const FUTURE = Date.parse("2026-12-01T00:00:00.000Z");
const PAST = Date.parse("2026-01-01T00:00:00.000Z");
const UID = "user-1";

function firestoreWithDocs(
  docs: Record<string, Record<string, unknown> | undefined>,
): EntitlementFirestore {
  return {
    doc(path: string) {
      return {
        async get() {
          const data = docs[path];
          return {
            exists: data !== undefined,
            data: () => data,
          };
        },
      };
    },
  };
}

function installDocs(docs: Record<string, Record<string, unknown> | undefined>): void {
  setEntitlementFirestoreGetter(() => firestoreWithDocs(docs));
}

afterEach(() => {
  resetEntitlementFirestoreGetter();
});

describe("isEntitledFromDocs", () => {
  it("complimentary freeAccess true is entitled", () => {
    assert.equal(isEntitledFromDocs({ override: { freeAccess: true } }, NOW), true);
  });

  it("active play without expiry is entitled", () => {
    assert.equal(isEntitledFromDocs({ play: { active: true } }, NOW), true);
  });

  it("active play with future expiry is entitled", () => {
    assert.equal(
      isEntitledFromDocs({ play: { active: true, expiryTimeMillis: FUTURE } }, NOW),
      true,
    );
  });

  it("active play with expired expiry is not entitled", () => {
    assert.equal(
      isEntitledFromDocs({ play: { active: true, expiryTimeMillis: PAST } }, NOW),
      false,
    );
  });

  it("missing docs are not entitled", () => {
    assert.equal(isEntitledFromDocs({}, NOW), false);
  });

  it("override without freeAccess is not entitled", () => {
    assert.equal(isEntitledFromDocs({ override: { note: "vip pending" } }, NOW), false);
    assert.equal(isEntitledFromDocs({ override: { freeAccess: false } }, NOW), false);
  });
});

describe("isUserEntitled", () => {
  it("complimentary override entitles even with expired play", async () => {
    installDocs({
      [entitlementDocPath(UID, "override")]: { freeAccess: true },
      [entitlementDocPath(UID, "play")]: { active: true, expiryTimeMillis: PAST },
    });
    assert.equal(await isUserEntitled(UID, NOW), true);
  });

  it("active play without expiry entitles", async () => {
    installDocs({
      [entitlementDocPath(UID, "play")]: { active: true, productId: "stratum_membership" },
    });
    assert.equal(await isUserEntitled(UID, NOW), true);
  });

  it("active play with future expiry entitles", async () => {
    installDocs({
      [entitlementDocPath(UID, "play")]: { active: true, expiryTimeMillis: FUTURE },
    });
    assert.equal(await isUserEntitled(UID, NOW), true);
  });

  it("expired play does not entitle", async () => {
    installDocs({
      [entitlementDocPath(UID, "play")]: { active: true, expiryTimeMillis: PAST },
    });
    assert.equal(await isUserEntitled(UID, NOW), false);
  });

  it("missing docs do not entitle", async () => {
    installDocs({});
    assert.equal(await isUserEntitled(UID, NOW), false);
  });

  it("override without freeAccess does not entitle", async () => {
    installDocs({
      [entitlementDocPath(UID, "override")]: { freeAccess: false },
    });
    assert.equal(await isUserEntitled(UID, NOW), false);
  });
});

describe("assertUserEntitled", () => {
  it("throws permission-denied when not entitled", async () => {
    installDocs({});
    await assert.rejects(
      () => assertUserEntitled(UID, NOW),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "permission-denied");
        assert.equal(err.message, MEMBERSHIP_REQUIRED_MESSAGE);
        return true;
      },
    );
  });

  it("resolves when complimentary", async () => {
    installDocs({
      [entitlementDocPath(UID, "override")]: { freeAccess: true },
    });
    await assertUserEntitled(UID, NOW);
  });
});
