import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type { DocumentReference, Transaction } from "firebase-admin/firestore";
import {
  MAX_IMAGE_BYTES,
  MAX_SCANS_PER_DAY,
  MAX_SCANS_PER_MONTH,
  admitIncrementScan,
  assertScanQuotaAllowed,
  effectiveDayCount,
  effectiveMonthCount,
  isAtDailyScanQuota,
  isAtMonthlyScanQuota,
  nextQuotaAfterScan,
  scanQuotaPath,
  utcDayKey,
  utcMonthKey,
  type ScanQuotaDoc,
  type ScanQuotaFirestore,
} from "./scanQuota";

/** In-memory ScanQuotaFirestore fake: runTransaction executes inline. */
function fakeQuotaFirestore(store: Map<string, Record<string, unknown>>): ScanQuotaFirestore {
  const pathOf = (ref: unknown): string => (ref as { __path: string }).__path;
  return {
    doc: (path: string) => ({ __path: path }) as unknown as DocumentReference,
    runTransaction: async <T>(fn: (tx: Transaction) => Promise<T>): Promise<T> => {
      const tx = {
        get: async (ref: unknown) => {
          const data = store.get(pathOf(ref));
          return {
            exists: data !== undefined,
            data: () => data,
          };
        },
        set: (ref: unknown, data: Record<string, unknown>) => {
          store.set(pathOf(ref), data);
        },
      } as unknown as Transaction;
      return fn(tx);
    },
  };
}

describe("utcMonthKey / utcDayKey", () => {
  it("formats UTC month and day with zero padding", () => {
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 0, 5))), "2026-01");
    assert.equal(utcDayKey(new Date(Date.UTC(2026, 0, 5))), "2026-01-05");
    assert.equal(utcDayKey(new Date(Date.UTC(2026, 11, 31, 23, 59))), "2026-12-31");
  });

  it("uses UTC boundary (not local) near day edges", () => {
    assert.equal(utcDayKey(new Date("2026-07-01T00:30:00.000Z")), "2026-07-01");
    assert.equal(utcDayKey(new Date("2026-06-30T23:59:59.999Z")), "2026-06-30");
  });
});

describe("effective counts and caps", () => {
  it("missing doc counts as zero", () => {
    assert.equal(effectiveMonthCount(undefined, "2026-07"), 0);
    assert.equal(effectiveDayCount(undefined, "2026-07-14"), 0);
    assert.equal(isAtMonthlyScanQuota(undefined, "2026-07"), false);
    assert.equal(isAtDailyScanQuota(undefined, "2026-07-14"), false);
  });

  it("caps are 30/month and 10/day", () => {
    assert.equal(MAX_SCANS_PER_MONTH, 30);
    assert.equal(MAX_SCANS_PER_DAY, 10);
    assert.equal(MAX_IMAGE_BYTES, 2 * 1024 * 1024);
  });

  it("monthly cap gates at MAX_SCANS_PER_MONTH", () => {
    const atCap: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 30,
      dayCount: 2,
    };
    assert.equal(isAtMonthlyScanQuota(atCap, "2026-07"), true);
    const under: ScanQuotaDoc = { ...atCap, monthCount: 29 };
    assert.equal(isAtMonthlyScanQuota(under, "2026-07"), false);
  });

  it("daily cap gates at MAX_SCANS_PER_DAY", () => {
    const atCap: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 5,
      dayCount: 10,
    };
    assert.equal(isAtDailyScanQuota(atCap, "2026-07-14"), true);
    const under: ScanQuotaDoc = { ...atCap, dayCount: 9 };
    assert.equal(isAtDailyScanQuota(under, "2026-07-14"), false);
  });

  it("month rollover restarts the monthly count", () => {
    const prev: ScanQuotaDoc = {
      monthKey: "2026-06",
      dayKey: "2026-06-30",
      monthCount: 30,
      dayCount: 10,
    };
    assert.equal(effectiveMonthCount(prev, "2026-07"), 0);
    assert.equal(isAtMonthlyScanQuota(prev, "2026-07"), false);
    assert.deepEqual(nextQuotaAfterScan(prev, "2026-07", "2026-07-01"), {
      monthKey: "2026-07",
      dayKey: "2026-07-01",
      monthCount: 1,
      dayCount: 1,
    });
  });

  it("day rollover restarts the daily count but keeps the month", () => {
    const prev: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-13",
      monthCount: 5,
      dayCount: 10,
    };
    assert.equal(effectiveDayCount(prev, "2026-07-14"), 0);
    assert.equal(isAtDailyScanQuota(prev, "2026-07-14"), false);
    assert.deepEqual(nextQuotaAfterScan(prev, "2026-07", "2026-07-14"), {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 6,
      dayCount: 1,
    });
  });

  it("negative or non-numeric counters count as zero", () => {
    const dirty = { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: -3, dayCount: NaN };
    assert.equal(effectiveMonthCount(dirty, "2026-07"), 0);
    assert.equal(effectiveDayCount(dirty, "2026-07-14"), 0);
  });

  it("scanQuotaPath is users/{uid}/incrementsScanQuota/current", () => {
    assert.equal(scanQuotaPath("uid-1"), "users/uid-1/incrementsScanQuota/current");
  });
});

describe("assertScanQuotaAllowed", () => {
  it("throws resource-exhausted at the monthly cap", async () => {
    const doc: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 30,
      dayCount: 1,
    };
    await assert.rejects(
      async () => assertScanQuotaAllowed(doc, "2026-07", "2026-07-14"),
      (err: { code?: string }) => {
        assert.equal(err.code, "resource-exhausted");
        return true;
      },
    );
  });

  it("throws resource-exhausted at the daily cap", async () => {
    const doc: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 3,
      dayCount: 10,
    };
    await assert.rejects(
      async () => assertScanQuotaAllowed(doc, "2026-07", "2026-07-14"),
      (err: { code?: string }) => {
        assert.equal(err.code, "resource-exhausted");
        return true;
      },
    );
  });

  it("allows scans under both caps", () => {
    assertScanQuotaAllowed(undefined, "2026-07", "2026-07-14");
  });
});

describe("admitIncrementScan", () => {
  const NOW = new Date("2026-07-14T12:00:00.000Z");

  it("creates the quota doc with 1/1 on first admission", async () => {
    const store = new Map<string, Record<string, unknown>>();
    await admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW });
    assert.deepEqual(
      { ...store.get(scanQuotaPath("uid-1")), updatedAt: undefined },
      {
        monthKey: "2026-07",
        dayKey: "2026-07-14",
        monthCount: 1,
        dayCount: 1,
        updatedAt: undefined,
      },
    );
  });

  it("increments month and day counters on repeat admission", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: 4, dayCount: 2 },
      ],
    ]);
    await admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW });
    const stored = store.get(scanQuotaPath("uid-1"))!;
    assert.equal(stored.monthCount, 5);
    assert.equal(stored.dayCount, 3);
  });

  it("rejects at the daily cap without incrementing", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: 4, dayCount: 10 },
      ],
    ]);
    await assert.rejects(
      () => admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW }),
      (err: { code?: string }) => {
        assert.equal(err.code, "resource-exhausted");
        return true;
      },
    );
    const stored = store.get(scanQuotaPath("uid-1"))!;
    assert.equal(stored.dayCount, 10);
    assert.equal(stored.monthCount, 4);
  });

  it("rejects at the monthly cap without incrementing", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: 30, dayCount: 2 },
      ],
    ]);
    await assert.rejects(
      () => admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW }),
      (err: { code?: string }) => {
        assert.equal(err.code, "resource-exhausted");
        return true;
      },
    );
    assert.equal(store.get(scanQuotaPath("uid-1"))!.monthCount, 30);
  });
});
