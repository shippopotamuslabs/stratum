"""Solver entrypoint contract: in-process (SOLVER_MODULE) and HTTP (SOLVER_URL).

Covers the transport half of the entrypoint documented in solver_client.py:
default module, module override, missing solve(), HTTP URL routing
(POST /solve/<major>), HTTP rejection mapping, and SOLVER_URL precedence.
"""

import copy
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

import solver_client
from solver_client import (
    DEFAULT_SOLVER_MODULE,
    load_solver_module,
    solve_in_process,
    solve_over_http,
    solve_request,
)


@pytest.fixture
def request_v1():
    from contract import DEFAULT_CONTRACT_ROOT, request_fixtures

    fixture = request_fixtures(DEFAULT_CONTRACT_ROOT, 1)[0]
    with open(fixture, encoding="utf-8") as f:
        return json.load(f)


def test_default_solver_module_is_stub(monkeypatch, request_v1):
    monkeypatch.delenv("SOLVER_MODULE", raising=False)
    monkeypatch.delenv("SOLVER_URL", raising=False)
    module = load_solver_module()
    assert module.__name__ == DEFAULT_SOLVER_MODULE == "stub_solver"
    response = solve_request(request_v1)
    assert response["modelVersion"] == "v1"


def test_solver_module_override(monkeypatch, request_v1):
    monkeypatch.setenv("SOLVER_MODULE", "stub_solver")
    monkeypatch.delenv("SOLVER_URL", raising=False)
    response = solve_request(request_v1)
    assert response["modelVersion"] == "v1"


def test_solver_module_without_solve_fails_loudly(monkeypatch, request_v1):
    monkeypatch.setenv("SOLVER_MODULE", "json")
    with pytest.raises(AttributeError, match="no callable 'solve"):
        solve_in_process(request_v1)


class _FakeSolverHandler(BaseHTTPRequestHandler):
    """Minimal fake of the real solver's HTTP surface (for client tests only)."""

    server_version = "FakeSolver/0"
    last_path: str | None = None

    def log_message(self, *args):
        pass

    def do_POST(self):
        type(self).last_path = self.path
        length = int(self.headers.get("Content-Length", 0))
        payload = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
        version = payload.get("modelVersion", "v1")
        major = version.lstrip("v").split(".")[0] if isinstance(version, str) else "?"
        expected_path = f"/solve/{major}"
        if self.path != expected_path:
            self._send(400, {"error": f"URL major {self.path} != body {version}"})
            return
        if major not in ("1",):
            self._send(422, {"error": f"unsupported major v{major}"})
            return
        import stub_solver

        # Reuse the stub's canned builder: validates the HTTP path end to end.
        self._send(200, stub_solver.solve_v1(copy.deepcopy(payload)))

    def _send(self, status, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


@pytest.fixture
def fake_solver_url():
    server = HTTPServer(("127.0.0.1", 0), _FakeSolverHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}"
    finally:
        server.shutdown()
        thread.join()


def test_http_posts_to_versioned_url(monkeypatch, fake_solver_url, request_v1):
    monkeypatch.setenv("SOLVER_URL", fake_solver_url)
    response = solve_request(request_v1)
    assert _FakeSolverHandler.last_path == "/solve/1"
    assert response["modelVersion"] == "v1"


def test_http_missing_version_routes_to_solve_v1(monkeypatch, fake_solver_url, request_v1):
    request_v1.pop("modelVersion", None)
    monkeypatch.setenv("SOLVER_URL", fake_solver_url)
    solve_request(request_v1)
    assert _FakeSolverHandler.last_path == "/solve/1"


def test_http_unknown_major_rejected(monkeypatch, fake_solver_url, request_v1):
    request_v1["modelVersion"] = "v3"
    monkeypatch.setenv("SOLVER_URL", fake_solver_url)
    with pytest.raises(ValueError, match="(?i)unsupported major|HTTP 422"):
        solve_request(request_v1)


def test_solver_url_wins_over_module(monkeypatch, fake_solver_url, request_v1):
    monkeypatch.setenv("SOLVER_URL", fake_solver_url)
    monkeypatch.setenv("SOLVER_MODULE", "stub_solver")
    solve_request(request_v1)
    assert _FakeSolverHandler.last_path == "/solve/1"


def test_http_unreachable_server_errors(monkeypatch, request_v1):
    monkeypatch.setenv("SOLVER_URL", "http://127.0.0.1:1")
    with pytest.raises(Exception):
        solve_over_http(request_v1)
