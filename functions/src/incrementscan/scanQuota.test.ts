import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type { DocumentReference, Transaction } from "firebase-admin/firestore";
import {
  MAX_SCANS_PER_DAY,
  MAX_SCANS_PER_MONTH,
  admitIncrementScan,
  assertScanQuotaAllowed,
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

function resourceExhausted(check: () => void | Promise<unknown>): Promise<void> {
  return assert.rejects(async () => check(), (err: { code?: string }) => {
    assert.equal(err.code, "resource-exhausted");
    return true;
  });
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

describe("quota caps and rollover (via assertScanQuotaAllowed)", () => {
  it("caps are 30/month and 10/day", () => {
    assert.equal(MAX_SCANS_PER_MONTH, 30);
    assert.equal(MAX_SCANS_PER_DAY, 10);
  });

  it("missing doc counts as zero", () => {
    assertScanQuotaAllowed(undefined, "2026-07", "2026-07-14");
  });

  it("monthly cap gates at MAX_SCANS_PER_MONTH", async () => {
    const atCap: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 30,
      dayCount: 2,
    };
    await resourceExhausted(() => assertScanQuotaAllowed(atCap, "2026-07", "2026-07-14"));
    const under: ScanQuotaDoc = { ...atCap, monthCount: 29 };
    assertScanQuotaAllowed(under, "2026-07", "2026-07-14");
  });

  it("daily cap gates at MAX_SCANS_PER_DAY", async () => {
    const atCap: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-14",
      monthCount: 5,
      dayCount: 10,
    };
    await resourceExhausted(() => assertScanQuotaAllowed(atCap, "2026-07", "2026-07-14"));
    const under: ScanQuotaDoc = { ...atCap, dayCount: 9 };
    assertScanQuotaAllowed(under, "2026-07", "2026-07-14");
  });

  it("month rollover restarts the monthly count", () => {
    const prev: ScanQuotaDoc = {
      monthKey: "2026-06",
      dayKey: "2026-06-30",
      monthCount: 30,
      dayCount: 10,
    };
    assertScanQuotaAllowed(prev, "2026-07", "2026-07-01");
  });

  it("day rollover restarts the daily count but keeps the month", async () => {
    const prev: ScanQuotaDoc = {
      monthKey: "2026-07",
      dayKey: "2026-07-13",
      monthCount: 5,
      dayCount: 10,
    };
    assertScanQuotaAllowed(prev, "2026-07", "2026-07-14");
    // Same-day doc at the daily cap still rejects.
    const sameDay: ScanQuotaDoc = { ...prev, dayKey: "2026-07-14" };
    await resourceExhausted(() => assertScanQuotaAllowed(sameDay, "2026-07", "2026-07-14"));
  });

  it("negative or non-numeric counters count as zero", () => {
    const dirty = { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: -3, dayCount: NaN };
    assertScanQuotaAllowed(dirty, "2026-07", "2026-07-14");
  });

  it("scanQuotaPath is users/{uid}/incrementsScanQuota/current", () => {
    assert.equal(scanQuotaPath("uid-1"), "users/uid-1/incrementsScanQuota/current");
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

  it("month rollover restarts at 1/1", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-06", dayKey: "2026-06-30", monthCount: 30, dayCount: 10 },
      ],
    ]);
    await admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW });
    const stored = store.get(scanQuotaPath("uid-1"))!;
    assert.equal(stored.monthKey, "2026-07");
    assert.equal(stored.dayKey, "2026-07-14");
    assert.equal(stored.monthCount, 1);
    assert.equal(stored.dayCount, 1);
  });

  it("day rollover restarts the daily count but keeps the month", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-07", dayKey: "2026-07-13", monthCount: 5, dayCount: 10 },
      ],
    ]);
    await admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW });
    const stored = store.get(scanQuotaPath("uid-1"))!;
    assert.equal(stored.monthCount, 6);
    assert.equal(stored.dayCount, 1);
  });

  it("negative or non-numeric counters admit as 1/1", async () => {
    const store = new Map<string, Record<string, unknown>>([
      [
        scanQuotaPath("uid-1"),
        { monthKey: "2026-07", dayKey: "2026-07-14", monthCount: -3, dayCount: NaN },
      ],
    ]);
    await admitIncrementScan("uid-1", { firestore: fakeQuotaFirestore(store), now: NOW });
    const stored = store.get(scanQuotaPath("uid-1"))!;
    assert.equal(stored.monthCount, 1);
    assert.equal(stored.dayCount, 1);
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
