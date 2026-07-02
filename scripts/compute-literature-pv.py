#!/usr/bin/env python3
"""Preview literature maintenance PV = 0.85 × MAV-mid for each muscle (half-set rounded).

Literature PV is a dose prior for preset users. True Pareto volume (80% of fitted peak
response) is computed in DoseResponseQuadraticFit when import history exists.
"""

from __future__ import annotations

# Keep in sync with VolumeLandmarkPriors.kt band selections
MUSCLE_BANDS: dict[str, tuple[tuple[float, float], tuple[float, float]]] = {
    "Chest": ((6, 12), (22, 26)),
    "Back": ((8, 14), (25, 28)),
    "Quad": ((6, 12), (20, 24)),
    "Hamstring": ((4, 10), (18, 22)),
    "Glute": ((6, 12), (22, 26)),
    "PosteriorDeltoid": ((12, 20), (35, 40)),
    "LateralDeltoid": ((6, 12), (26, 30)),
    "AnteriorDeltoid": ((4, 8), (16, 20)),
    "Tricep": ((12, 18), (30, 34)),
    "Bicep": ((12, 18), (30, 34)),
    "Trap": ((4, 8), (12, 20)),
    "Calf": ((6, 12), (20, 24)),
    "Forearm": ((2, 4), (8, 16)),
    "Ab": ((0, 4), (25, 28)),
    "LowerBack": ((0, 4), (10, 18)),
    "Abductor": ((2, 4), (8, 16)),
    "Adductor": ((2, 4), (8, 16)),
}

PV_MAV_FRACTION = 0.85


def mids(bands: tuple[tuple[float, float], tuple[float, float]]) -> tuple[float, float, float, float]:
    (mev_low, mev_high), (mrv_low, mrv_high) = bands
    mev = (mev_low + mev_high) / 2.0
    mrv = (mrv_low + mrv_high) / 2.0
    mav = (mev_high + mrv_low) / 2.0
    mv = max(2.0, mev * 0.45)
    return mev, mav, mrv, mv


def literature_pv(mav: float, mv: float) -> float:
    raw = mav * PV_MAV_FRACTION
    rounded = round(raw * 2) / 2.0
    return max(mv, min(mav, rounded))


def main() -> None:
    print(f"Literature PV = {PV_MAV_FRACTION} × MAV-mid (clamped to MV..MAV, half-set rounded)\n")
    print(f"{'Muscle':22} MEV   MAV   MRV   MV    PV")
    for muscle, bands in MUSCLE_BANDS.items():
        mev, mav, mrv, mv = mids(bands)
        pv = literature_pv(mav, mv)
        print(f"{muscle:22} {mev:4.1f} {mav:4.1f} {mrv:4.1f} {mv:4.1f} {pv:4.1f}")


if __name__ == "__main__":
    main()
