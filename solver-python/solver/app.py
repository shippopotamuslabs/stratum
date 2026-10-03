"""Stdlib HTTP service for program synthesis (STR-45).

Endpoints:
- ``POST /solve/v1`` — v1 request JSON body -> v1 response JSON body.
  The body's ``modelVersion`` major must match the URL major (missing counts
  as v1 and matches ``/solve/v1`` only); an unknown major is a 400 JSON error.
- ``GET /health`` — liveness probe.

No third-party web framework (stdlib ``http.server`` only); OR-Tools is the
only runtime dependency beyond the standard library.
"""

from __future__ import annotations

import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from .model import (
    SolverConfigError,
    SolverInfeasibleError,
    solve_request,
)
from .wire import MODEL_VERSION_V1, decode_request, encode_response, parse_model_version
from .wire import WireError

SUPPORTED_MAJOR = 1
DEFAULT_MAX_TIME_SECONDS = 120.0
MAX_BODY_BYTES = 8 * 1024 * 1024


def _max_time_seconds() -> float:
    try:
        return float(os.environ.get("SOLVER_MAX_TIME_SECONDS", DEFAULT_MAX_TIME_SECONDS))
    except ValueError:
        return DEFAULT_MAX_TIME_SECONDS


def _num_search_workers():
    raw = os.environ.get("SOLVER_NUM_SEARCH_WORKERS")
    if raw is None or raw == "":
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def _error(status: int, message: str) -> tuple[int, dict]:
    return (status, {"error": message})


def handle_solve(url_major: int, payload: dict) -> tuple[int, dict]:
    """Solve a raw request payload for URL major ``url_major``."""
    try:
        major, _version = parse_model_version(payload.get("modelVersion"))
    except WireError as e:
        return _error(400, str(e))
    if major != url_major:
        return _error(
            400,
            f"Body modelVersion major v{major} does not match URL major v{url_major}",
        )
    try:
        decoded = decode_request(payload)
    except WireError as e:
        return _error(400, str(e))
    try:
        solution = solve_request(
            decoded,
            max_time_seconds=_max_time_seconds(),
            num_search_workers=_num_search_workers(),
        )
    except SolverConfigError as e:
        return _error(422, str(e))
    except SolverInfeasibleError as e:
        return _error(422, str(e))
    except Exception as e:  # defensive: never leak a traceback to clients
        return _error(500, f"Solver failed: {e}")
    return (200, encode_response(solution))


class _Handler(BaseHTTPRequestHandler):
    server_version = "StratumSolver/1"

    def log_message(self, fmt, *args):  # keep logs single-line on stdout
        print(f"solver: {self.address_string()} {fmt % args}")

    def _send_json(self, status: int, body: dict) -> None:
        raw = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):  # noqa: N802
        path = urlparse(self.path).path
        if path == "/health":
            self._send_json(
                200,
                {
                    "status": "ok",
                    "modelVersion": MODEL_VERSION_V1,
                    "solver": "cp-sat-python",
                },
            )
            return
        self._send_json(404, {"error": f"Not found: {path}"})

    def do_POST(self):  # noqa: N802
        path = urlparse(self.path).path
        if path == "/solve/v1":
            url_major = 1
        elif path.startswith("/solve/v"):
            # Message text is pinned by the contract suite (match="unsupported major").
            self._send_json(400, {"error": f"unsupported major in path: {path} (server supports v1)"})
            return
        else:
            self._send_json(404, {"error": f"Not found: {path}"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY_BYTES:
            self._send_json(400, {"error": "Missing or oversized request body"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as e:
            self._send_json(400, {"error": f"Invalid JSON body: {e}"})
            return
        if not isinstance(payload, dict):
            self._send_json(400, {"error": "Request body must be a JSON object"})
            return
        started = time.time()
        status, body = handle_solve(url_major, payload)
        print(f"solver: POST {path} -> {status} in {time.time() - started:.2f}s")
        self._send_json(status, body)


def run(host: str = "0.0.0.0", port: int = 8080) -> None:  # noqa: S104
    server = ThreadingHTTPServer((host, port), _Handler)
    print(
        f"solver: listening on {host}:{port} "
        f"(max_time_s={_max_time_seconds()}, workers={_num_search_workers()})"
    )
    server.serve_forever()


if __name__ == "__main__":
    run(
        host=os.environ.get("SOLVER_HOST", "0.0.0.0"),  # noqa: S104
        port=int(os.environ.get("PORT", os.environ.get("SOLVER_PORT", "8080"))),
    )
