"""Stratum program-synthesis solver package (STR-45).

The contract-test entrypoint (``solver-contract/tests/solver_client.py``) calls
``solve(request_dict)`` on the module named by ``$SOLVER_MODULE``. With
``PYTHONPATH=solver-python`` and ``SOLVER_MODULE=solver``, that resolves here.
"""

from __future__ import annotations


def solve(request: dict) -> dict:
    """In-process v1 entrypoint: raw request dict -> v1 response dict.

    Uses production solve defaults (30 s deadline, OR-Tools worker default).
    Raises ValueError (via WireError) on malformed/unsupported payloads.
    """
    from .model import solve_payload

    return solve_payload(request)
