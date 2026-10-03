"""HTTP service tests: version dispatch, JSON errors, health, live round-trip."""

import json
import threading
import urllib.request

import pytest

from solver.app import _Handler, handle_solve
from solver.model import solve_payload

from .support import tiny_request


def test_body_major_must_match_url():
    payload = tiny_request()
    payload["modelVersion"] = "v1"
    # Unknown URL major -> rejected before any solve.
    status, body = handle_solve(2, payload)
    assert status == 400
    assert "error" in body


def test_unknown_body_major_rejected_as_json_error():
    payload = tiny_request()
    payload["modelVersion"] = "v2"
    status, body = handle_solve(1, payload)
    assert status == 400
    assert "error" in body


def test_missing_body_version_matches_solve_v1(monkeypatch):
    monkeypatch.setenv("SOLVER_MAX_TIME_SECONDS", "15")
    payload = tiny_request()
    del payload["modelVersion"]
    status, body = handle_solve(1, payload)
    assert status == 200
    assert body["modelVersion"] == "v1"
    assert len(body["sessions"]) == 1


def test_malformed_payload_is_400():
    status, body = handle_solve(1, {"modelVersion": "v1"})
    assert status == 400
    assert "error" in body


def test_tiny_request_solves_with_low_tier_property():
    from .support import assert_no_back_to_back_low_tier_small_isolation

    payload = tiny_request()
    response = solve_payload(payload, max_time_seconds=15.0)
    assert len(response["sessions"]) == 1
    assert_no_back_to_back_low_tier_small_isolation(payload, response)


class TestLiveServer:
    @pytest.fixture
    def server(self, monkeypatch):
        from http.server import ThreadingHTTPServer

        monkeypatch.setenv("SOLVER_MAX_TIME_SECONDS", "15")
        httpd = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        port = httpd.server_address[1]
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        yield port
        httpd.shutdown()

    def _post(self, port, path, body):
        req = urllib.request.Request(
            f"http://127.0.0.1:{port}{path}",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return resp.status, json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read().decode())

    def test_health(self, server):
        with urllib.request.urlopen(
            f"http://127.0.0.1:{server}/health", timeout=10
        ) as resp:
            assert resp.status == 200
            body = json.loads(resp.read().decode())
            assert body["status"] == "ok"

    def test_solve_v1_round_trip(self, server):
        status, body = self._post(server, "/solve/v1", tiny_request())
        assert status == 200
        assert body["modelVersion"] == "v1"
        assert len(body["sessions"]) == 1

    def test_solve_v2_rejected(self, server):
        payload = tiny_request()
        payload["modelVersion"] = "v2"
        status, body = self._post(server, "/solve/v1", payload)
        assert status == 400
        assert "error" in body
