import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  MAX_IMAGE_BYTES,
  SCAN_IMAGE_MIME_TYPES,
  buildScanResult,
  inferEquipmentIncrements,
  parseScanModelContent,
  resolveScanImageDataUri,
  resolveScanVisionConfig,
} from "./inferEquipmentIncrements";
import { invokeCallable } from "../testUtils/invokeCallable.test-helper";
import {
  entitlementDocPath,
  resetEntitlementFirestoreGetter,
  setEntitlementFirestoreGetter,
  type EntitlementFirestore,
} from "../entitlement/assertEntitled";

const UID = "user-1";
const TINY_JPEG_BASE64 = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]).toString("base64");

function validPayload() {
  return { imageBase64: TINY_JPEG_BASE64, mimeType: "image/jpeg" };
}

function installEntitled(): void {
  const docs: Record<string, Record<string, unknown> | undefined> = {
    [entitlementDocPath(UID, "override")]: { freeAccess: true },
  };
  const db: EntitlementFirestore = {
    doc(path: string) {
      return {
        async get() {
          const data = docs[path];
          return { exists: data !== undefined, data: () => data };
        },
      };
    },
  };
  setEntitlementFirestoreGetter(() => db);
}

function installNoEntitlement(): void {
  setEntitlementFirestoreGetter(() => ({
    doc() {
      return {
        async get() {
          return { exists: false, data: () => undefined };
        },
      };
    },
  }));
}

function runCallable() {
  return (request: unknown) => invokeCallable(inferEquipmentIncrements, request);
}

afterEach(() => {
  resetEntitlementFirestoreGetter();
});

describe("resolveScanImageDataUri", () => {
  it("folds a jpeg payload into a data URI", () => {
    assert.equal(
      resolveScanImageDataUri(validPayload()),
      `data:image/jpeg;base64,${TINY_JPEG_BASE64}`,
    );
  });

  it("accepts png and webp", () => {
    for (const mimeType of ["image/png", "image/webp"]) {
      assert.ok(resolveScanImageDataUri({ imageBase64: TINY_JPEG_BASE64, mimeType }));
    }
    assert.deepEqual([...SCAN_IMAGE_MIME_TYPES], ["image/jpeg", "image/png", "image/webp"]);
  });

  it("rejects unknown mime types", async () => {
    await assert.rejects(
      async () =>
        resolveScanImageDataUri({ imageBase64: TINY_JPEG_BASE64, mimeType: "image/gif" }),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });

  it("rejects empty or non-string payloads", async () => {
    for (const data of [undefined, null, {}, { imageBase64: "", mimeType: "image/jpeg" }]) {
      await assert.rejects(
        async () => resolveScanImageDataUri(data),
        (err: { code?: string }) => {
          assert.equal(err.code, "invalid-argument");
          return true;
        },
      );
    }
  });

  it("rejects non-base64 content", async () => {
    await assert.rejects(
      async () =>
        resolveScanImageDataUri({ imageBase64: "not base64!!! ***", mimeType: "image/jpeg" }),
      (err: { code?: string }) => {
        assert.equal(err.code, "invalid-argument");
        return true;
      },
    );
  });

  it("MAX_IMAGE_BYTES is a 2 MiB server-side cap", () => {
    assert.equal(MAX_IMAGE_BYTES, 2 * 1024 * 1024);
  });

  it("rejects images over MAX_IMAGE_BYTES", async () => {
    const oversize = Buffer.alloc(MAX_IMAGE_BYTES + 1, 7).toString("base64");
    await assert.rejects(
      async () => resolveScanImageDataUri({ imageBase64: oversize, mimeType: "image/jpeg" }),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(err.message ?? "", /too large/);
        return true;
      },
    );
  });

  it("accepts an image exactly at MAX_IMAGE_BYTES", () => {
    const exact = Buffer.alloc(MAX_IMAGE_BYTES, 7).toString("base64");
    assert.ok(resolveScanImageDataUri({ imageBase64: exact, mimeType: "image/jpeg" }));
  });
});

describe("resolveScanVisionConfig", () => {
  it("fails closed when the API key is missing", () => {
    assert.throws(
      () => resolveScanVisionConfig({ OPENROUTER_VISION_MODEL: "some/vision" }),
      (err: { code?: string }) => {
        assert.equal(err.code, "failed-precondition");
        return true;
      },
    );
  });

  it("fails closed when the vision model is missing (no fallback)", () => {
    assert.throws(
      () => resolveScanVisionConfig({ OPENROUTER_API_KEY: "key" }),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "failed-precondition");
        assert.match(err.message ?? "", /OPENROUTER_VISION_MODEL/);
        return true;
      },
    );
  });

  it("returns key and model when both are present", () => {
    assert.deepEqual(
      resolveScanVisionConfig({ OPENROUTER_API_KEY: "key", OPENROUTER_VISION_MODEL: "m/v" }),
      { apiKey: "key", model: "m/v" },
    );
  });
});

describe("parseScanModelContent", () => {
  it("throws internal on empty content", () => {
    for (const content of [null, undefined, ""]) {
      assert.throws(
        () => parseScanModelContent(content),
        (err: { code?: string }) => {
          assert.equal(err.code, "internal");
          return true;
        },
      );
    }
  });

  it("throws internal on malformed JSON", () => {
    assert.throws(
      () => parseScanModelContent("{not json"),
      (err: { code?: string }) => {
        assert.equal(err.code, "internal");
        return true;
      },
    );
  });

  it("parses a recorded lb fixture with micro stops", () => {
    const scan = parseScanModelContent(
      JSON.stringify({
        unit: "lb",
        tiers: [
          { lower: 10, upper: 50, step: 10 },
          { lower: 50, upper: null, step: 5 },
        ],
        microStops: [2.5],
      }),
    );
    assert.deepEqual(scan, {
      unit: "lb",
      tiers: [
        { lower: 10, upper: 50, step: 10 },
        { lower: 50, upper: null, step: 5 },
      ],
      microStops: [2.5],
    });
  });

  it("sanitizes garbage model JSON to empty tiers (never throws)", () => {
    const scan = parseScanModelContent(
      JSON.stringify({
        unit: "stones",
        tiers: [{ lower: -5, upper: "a lot", step: 0 }],
        microStops: ["pin"],
        extra: "dropped",
      }),
    );
    assert.deepEqual(scan, { unit: "lb", tiers: [], microStops: [] });
  });
});

describe("buildScanResult", () => {
  it("spreads the scan with a _meta v1 baseline and no deprecation", () => {
    const scan = {
      unit: "lb" as const,
      tiers: [{ lower: 10, upper: 50, step: 10 }],
      microStops: [2.5],
    };
    assert.deepEqual(buildScanResult(scan), {
      unit: "lb",
      tiers: [{ lower: 10, upper: 50, step: 10 }],
      microStops: [2.5],
      _meta: { version: 1 },
    });
  });

  it("does not mutate the input scan", () => {
    const scan = { unit: "kg" as const, tiers: [], microStops: [] };
    buildScanResult(scan);
    assert.deepEqual(scan, { unit: "kg", tiers: [], microStops: [] });
  });
});

describe("inferEquipmentIncrements callable", () => {
  it("rejects unauthenticated callers", async () => {
    await assert.rejects(
      () => runCallable()({ auth: null, data: validPayload() }),
      (err: { code?: string }) => {
        assert.equal(err.code, "unauthenticated");
        return true;
      },
    );
  });

  it("rejects non-entitled callers with permission-denied", async () => {
    installNoEntitlement();
    await assert.rejects(
      () => runCallable()({ auth: { uid: UID }, data: validPayload() }),
      (err: { code?: string }) => {
        assert.equal(err.code, "permission-denied");
        return true;
      },
    );
  });

  it("fails closed with failed-precondition when the vision secret is missing", async () => {
    installEntitled();
    const savedKey = process.env.OPENROUTER_API_KEY;
    const savedModel = process.env.OPENROUTER_VISION_MODEL;
    delete process.env.OPENROUTER_API_KEY;
    delete process.env.OPENROUTER_VISION_MODEL;
    try {
      await assert.rejects(
        () => runCallable()({ auth: { uid: UID }, data: validPayload() }),
        (err: { code?: string }) => {
          assert.equal(err.code, "failed-precondition");
          return true;
        },
      );
    } finally {
      if (savedKey !== undefined) process.env.OPENROUTER_API_KEY = savedKey;
      if (savedModel !== undefined) process.env.OPENROUTER_VISION_MODEL = savedModel;
    }
  });
});
