import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  clampBackOffRir,
  MAJOR_COMPOUND_PATTERNS,
  schemeForTrainingAge,
  TOP_SET_RIR,
} from "./programRules";

describe("programRules", () => {
  describe("PROG-GEN-705 MAJOR_COMPOUND_PATTERNS is the exact §2.3 list", () => {
    it("contains exactly the seven major-compound patterns", () => {
      assert.deepEqual(
        [...MAJOR_COMPOUND_PATTERNS].sort(),
        [
          "HipHinge",
          "HorizontalPull",
          "HorizontalPress",
          "InclinePress",
          "SquatPattern",
          "VerticalPull",
          "VerticalPress",
        ],
      );
    });

    it("TOP_SET_RIR is 2", () => {
      assert.equal(TOP_SET_RIR, 2);
    });
  });

  describe("PROG-GEN-705 schemeForTrainingAge", () => {
    it("returns Linear for Beginner", () => {
      assert.equal(schemeForTrainingAge("Beginner"), "Linear");
    });

    it("returns Double for every non-Beginner training age", () => {
      assert.equal(schemeForTrainingAge("EarlyIntermediate"), "Double");
      assert.equal(schemeForTrainingAge("Intermediate"), "Double");
      assert.equal(schemeForTrainingAge("LateIntermediate"), "Double");
      assert.equal(schemeForTrainingAge("Advanced"), "Double");
    });
  });

  describe("PROG-GEN-704 clampBackOffRir clamps to the §2.3 table", () => {
    it("clamps a major-compound out-of-range proposal to the default 2", () => {
      assert.equal(clampBackOffRir(true, 5), 2);
    });

    it("preserves an in-range major-compound proposal", () => {
      assert.equal(clampBackOffRir(true, 1), 1);
      assert.equal(clampBackOffRir(true, 2), 2);
    });

    it("returns 0 for non-compound lifts regardless of proposal", () => {
      assert.equal(clampBackOffRir(false, 3), 0);
    });
  });
});
