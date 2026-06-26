"""Sandboxed Python conversion runner for historical import."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from firebase_admin import initialize_app, storage
from firebase_functions import https_fn

initialize_app()

BLOCKED = (
    "import os",
    "import subprocess",
    "import socket",
    "open(",
    "__import__",
    "eval(",
    "exec(",
    "compile(",
)


def validate_script(script: str) -> None:
    lower = script.lower()
    for token in BLOCKED:
        if token in lower:
            raise ValueError(f"Blocked token in script: {token}")


def run_script(script: str, input_text: str) -> str:
    validate_script(script)
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        script_path = tmp_path / "converter.py"
        input_path = tmp_path / "input.txt"
        output_path = tmp_path / "output.json"
        script_path.write_text(script, encoding="utf-8")
        input_path.write_text(input_text, encoding="utf-8")
        wrapper = f"""
import json
import sys
from pathlib import Path

INPUT_PATH = Path({repr(str(input_path))})
OUTPUT_PATH = Path({repr(str(output_path))})

with INPUT_PATH.open(encoding="utf-8") as f:
    raw_input = f.read()

# User script must define convert(raw_input: str) -> dict
"""
        full_script = wrapper + script + """

if "convert" not in dir():
    raise RuntimeError("Script must define convert(raw_input: str) -> dict")
result = convert(raw_input)
OUTPUT_PATH.write_text(json.dumps(result), encoding="utf-8")
"""
        proc = subprocess.run(
            [sys.executable, "-c", full_script],
            capture_output=True,
            text=True,
            timeout=60,
            cwd=tmp,
        )
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr or proc.stdout or "Script execution failed")
        return output_path.read_text(encoding="utf-8")


@https_fn.on_call(timeout_sec=120, memory=512)
def run_conversion_script(req: https_fn.CallableRequest) -> dict:
    if req.auth is None:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.UNAUTHENTICATED,
            message="Sign-in required.",
        )
    data = req.data or {}
    script_path = data.get("scriptStoragePath")
    input_path = data.get("inputStoragePath")
    if not script_path or not input_path:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            message="scriptStoragePath and inputStoragePath are required.",
        )
    bucket = storage.bucket()
    script_bytes = bucket.blob(script_path).download_as_bytes()
    input_bytes = bucket.blob(input_path).download_as_bytes()
    script = script_bytes.decode("utf-8")
    input_text = input_bytes.decode("utf-8")
    try:
        output = run_script(script, input_text)
        parsed = json.loads(output)
        sets_count = len(parsed.get("sets", []))
        sessions_count = len(parsed.get("sessions", []))
        return {
            "outputJson": output,
            "rowCount": sets_count,
            "sessionCount": sessions_count,
        }
    except Exception as exc:  # noqa: BLE001
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INTERNAL,
            message=str(exc),
        ) from exc


@https_fn.on_call(timeout_sec=120, memory=1024)
def embed_exercise_names(req: https_fn.CallableRequest) -> dict:
    if req.auth is None:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.UNAUTHENTICATED,
            message="Sign-in required.",
        )
    data = req.data or {}
    names = data.get("names")
    if not isinstance(names, list) or not names:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            message="names must be a non-empty list of strings.",
        )
    if len(names) > 200:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            message="names exceeds maximum batch size of 200.",
        )
    try:
        from embedding.lift_name_embedder import EMBEDDING_DIM, embed_texts
        from embedding.normalize import normalize_for_matching

        normalized = [normalize_for_matching(str(name)) for name in names]
        vectors = embed_texts(normalized)
        embeddings = {
            str(name): vector
            for name, vector in zip(names, vectors, strict=True)
            if len(vector) == EMBEDDING_DIM
        }
        return {"embeddings": embeddings, "dimensions": EMBEDDING_DIM}
    except Exception as exc:  # noqa: BLE001
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INTERNAL,
            message=str(exc),
        ) from exc
