import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

CONTRACT_DIR = Path(__file__).resolve().parents[2] / "solver-contract"
FIXTURES_DIR = CONTRACT_DIR / "fixtures" / "v1"
SCHEMA_DIR = CONTRACT_DIR / "schema" / "v1"


def fixture_deadline() -> float:
    try:
        return float(os.environ.get("SOLVER_TEST_DEADLINE_SECONDS", "60"))
    except ValueError:
        return 60.0
