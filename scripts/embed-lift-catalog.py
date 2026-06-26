#!/usr/bin/env python3
"""Pre-embed seed catalog lift names into a binary asset for the Android APK."""

from __future__ import annotations

import json
import struct
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

from embedding.lift_name_embedder import EMBEDDING_DIM, MODEL_ID, embed_texts  # noqa: E402
from embedding.normalize import normalize_for_matching  # noqa: E402

SEED_PATH = REPO_ROOT / "core/data/src/main/assets/lifts/seed-lifts.json"
OUT_BIN = REPO_ROOT / "core/data/src/main/assets/lifts/lift-name-embeddings.bin"
OUT_META = REPO_ROOT / "core/data/src/main/assets/lifts/lift-name-embeddings.meta.json"

MAGIC = b"STRM"
VERSION = 1


def main() -> None:
    lifts = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    entries: list[tuple[str, str]] = []
    for lift in lifts:
        lift_id = lift["id"]
        name = lift["name"]
        normalized = normalize_for_matching(name)
        entries.append((lift_id, normalized))

    vectors = embed_texts([text for _, text in entries])
    if len(vectors) != len(entries):
        raise RuntimeError("Embedding count mismatch.")

    for vector in vectors:
        if len(vector) != EMBEDDING_DIM:
            raise RuntimeError(f"Expected dim {EMBEDDING_DIM}, got {len(vector)}")

    OUT_BIN.parent.mkdir(parents=True, exist_ok=True)
    with OUT_BIN.open("wb") as out:
        out.write(MAGIC)
        out.write(struct.pack("<HH I", VERSION, EMBEDDING_DIM, len(entries)))
        for (lift_id, _), vector in zip(entries, vectors, strict=True):
            encoded = lift_id.encode("utf-8")
            out.write(struct.pack("<H", len(encoded)))
            out.write(encoded)
            out.write(struct.pack(f"<{EMBEDDING_DIM}f", *vector))

    OUT_META.write_text(
        json.dumps(
            {
                "modelId": MODEL_ID,
                "dimensions": EMBEDDING_DIM,
                "liftCount": len(entries),
                "format": "lift-name-embeddings.bin v1",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(entries)} lift embeddings to {OUT_BIN}")


if __name__ == "__main__":
    main()
