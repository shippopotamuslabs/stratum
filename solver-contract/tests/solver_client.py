"""Solver entrypoint: how tests (and later CI integration) invoke the solver.

Two transports, selected by environment (see README.md):

1. In-process (default)::

       solver.solve(request_dict) -> response_dict

   The module is named by ``$SOLVER_MODULE`` (default: ``stub_solver``, the
   canned dev solver shipped in this directory). The real Python solver
   replaces it at integration by setting ``SOLVER_MODULE`` to its importable
   module, which MUST expose the same ``solve`` function with these duties:

   * accept a request dict (parsed JSON); IGNORE unknown fields, treat a
     missing ``modelVersion`` as v1, accept ``v<major>.<minor>`` as its major;
   * return a response dict validating against
     ``schema/v<major>/response.schema.json`` with the request's major;
   * raise ``ValueError`` mentioning "unsupported major" for unknown majors.

2. HTTP: when ``$SOLVER_URL`` is set, ``POST $SOLVER_URL/solve/v<major>``
   with the request as the JSON body, where ``<major>`` is the request's
   dispatch major (missing ``modelVersion`` counts as v1, per §3 it matches
   ``/solve/v1`` only). Servers MUST reject a body whose ``modelVersion``
   major differs from the URL major, and MUST use a 4xx status for
   unsupported majors; the client surfaces 4xx as ``ValueError``.

``$SOLVER_URL`` wins when both variables are set.
"""

from __future__ import annotations

import importlib
import json
import os
import urllib.error
import urllib.request

from contract import major_of

SOLVER_MODULE_ENV = "SOLVER_MODULE"
SOLVER_URL_ENV = "SOLVER_URL"
DEFAULT_SOLVER_MODULE = "stub_solver"


def load_solver_module(name: str | None = None):
    """Import and return the configured in-process solver module.

    The tests directory is on ``sys.path`` via pytest's rootdir handling
    (see README.md); overrides like ``SOLVER_MODULE=solver`` resolve through
    the caller's ``PYTHONPATH``.
    """
    module_name = name or os.environ.get(SOLVER_MODULE_ENV, DEFAULT_SOLVER_MODULE)
    return importlib.import_module(module_name)


def solve_in_process(request: dict, module_name: str | None = None) -> dict:
    """Call ``solve`` on the configured in-process solver module."""
    module = load_solver_module(module_name)
    solve = getattr(module, "solve", None)
    if not callable(solve):
        raise AttributeError(
            f"solver module {module.__name__!r} exposes no callable 'solve(request) -> response'"
        )
    return solve(request)


def solve_over_http(request: dict, base_url: str | None = None) -> dict:
    """POST the request to ``<base>/solve/v<major>`` and return the JSON body."""
    base = (base_url or os.environ[SOLVER_URL_ENV]).rstrip("/")
    major = major_of(request)
    url = f"{base}/solve/v{major}"
    body = json.dumps(request).encode("utf-8")
    http_request = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urllib.request.urlopen(http_request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:500]
        if 400 <= e.code < 500:
            raise ValueError(f"solver rejected request (HTTP {e.code}): {detail}") from e
        raise RuntimeError(f"solver HTTP error {e.code}: {detail}") from e


def solve_request(request: dict) -> dict:
    """Solve via HTTP when ``$SOLVER_URL`` is set, else in-process."""
    if os.environ.get(SOLVER_URL_ENV):
        return solve_over_http(request)
    return solve_in_process(request)
