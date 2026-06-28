import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";

describe("programPayloadContext", () => {
  it("merges personal and literature landmarks per muscle", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        provenLifts: [],
        availableLifts: [],
        responseProfile: {
          volumeLandmarks: [
            {
              muscle: "Quad",
              estimatedMev: 10,
              estimatedMav: 16,
              estimatedMrv: 22,
              estimatedPv: 13,
            },
          ],
          volumeResponses: [],
          liftEffectiveness: [],
          epochsAnalyzed: 1,
          analysisWindowStart: "2025-01-01T00:00:00Z",
          analysisWindowEnd: "2025-06-01T00:00:00Z",
          informational: { volumeResponses: [], liftEffectiveness: [] },
        },
        literatureVolumeLandmarks: [
          {
            muscle: "Quad",
            estimatedMev: 8,
            estimatedMav: 14,
            estimatedMrv: 20,
            estimatedPv: 11,
          },
          {
            muscle: "Chest",
            estimatedMev: 8,
            estimatedMav: 14,
            estimatedMrv: 20,
            estimatedPv: 11,
          },
        ],
      }),
    );
    assert.equal(context.volumeLandmarksByMuscle.get("Quad")?.estimatedMav, 16);
    assert.equal(context.volumeLandmarksByMuscle.get("Quad")?.source, "personal");
    assert.equal(context.volumeLandmarksByMuscle.get("Chest")?.source, "literature");
  });
});
