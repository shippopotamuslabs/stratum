import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { isFirestoreNotFound } from "./safeJobUpdate";

describe("isFirestoreNotFound", () => {
  it("recognizes common Admin/gRPC not-found codes", () => {
    assert.equal(isFirestoreNotFound({ code: 5 }), true);
    assert.equal(isFirestoreNotFound({ code: "not-found" }), true);
    assert.equal(isFirestoreNotFound({ code: "NOT_FOUND" }), true);
    assert.equal(isFirestoreNotFound({ code: "permission-denied" }), false);
    assert.equal(isFirestoreNotFound(null), false);
    assert.equal(isFirestoreNotFound("nope"), false);
  });
});
