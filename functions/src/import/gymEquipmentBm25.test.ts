import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { matchEquipmentEnum } from "./gymEquipmentBm25";

describe("matchEquipmentEnum", () => {
  it("matches exact enum strings", () => {
    assert.equal(matchEquipmentEnum("CableStation"), "CableStation");
  });

  it("recovers common hallucinated cable labels", () => {
    assert.equal(matchEquipmentEnum("CableMachine"), "CableStation");
  });

  it("recovers camel-case enum typos with token overlap", () => {
    assert.equal(matchEquipmentEnum("Lat Pulldown Machine"), "LatPulldownMachine");
  });

  it("returns null for unrelated garbage", () => {
    assert.equal(matchEquipmentEnum("QuantumFluxCapacitor"), null);
  });
});
