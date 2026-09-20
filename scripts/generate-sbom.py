#!/usr/bin/env python3
"""Generate a CycloneDX 1.5 SBOM from Gradle dependency-tree output.

Usage:
    ./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q \\
        | python3 scripts/generate-sbom.py > bom.cdx.json

Reads the Gradle dependency tree from stdin, extracts resolved
``group:name:version`` triples (following ``->`` upgrade arrows, skipping
``(*)`` duplicate markers and ``(c)`` dependency-constraint markers), and
writes a CycloneDX 1.5 JSON SBOM with Maven purls to stdout.

Fail-closed behavior: any dependency line containing ``FAILED`` (an
unresolved artifact, e.g. from a network failure during resolution) makes
the generator exit nonzero and print the offending lines, so a partial
dependency tree can never produce a clean-looking SBOM. An empty result
(no resolved dependencies at all) is likewise rejected. Both guards keep
OSV from scanning an incomplete SBOM and reporting success.

The SBOM is consumed by OSV-Scanner in CI (see the ``osv-scan`` job in
``.gitlab-ci.yml`` and ``docs/security-scanning.md``):

    osv-scanner scan source -L bom.cdx.json
"""

import json
import re
import sys

DEP_RE = re.compile(
    r"(?P<group>[A-Za-z0-9_.\-]+):(?P<name>[A-Za-z0-9_.\-]+):(?P<version>[A-Za-z0-9_.\-+]+)"
)
UPGRADE_RE = re.compile(r"->\s*([A-Za-z0-9_.\-+]+)")


def parse_dependencies(text: str) -> tuple[dict[tuple[str, str], str], list[str]]:
    """Extract resolved (group, name) -> version from dependency-tree text.

    Returns the resolved mapping plus a list of the raw FAILED (unresolved
    dependency) lines. FAILED entries are *not* dropped silently: callers
    must treat a non-empty failed list as a hard error (see ``main``), so a
    partial tree never yields a clean-looking SBOM.
    """
    resolved: dict[tuple[str, str], str] = {}
    failed: list[str] = []
    for line in text.splitlines():
        if "(*)" in line or "(c)" in line:
            # (*)  = already listed above (omitted duplicate)
            # (c)  = dependency constraint, not an actual dependency
            continue
        if "FAILED" in line:
            # Unresolved dependency (e.g. network failure during resolution).
            # Its version is unknown, so it must not enter the SBOM — and the
            # caller must fail rather than scan the partial result.
            failed.append(line.strip())
            continue
        if "project :" in line:
            # Local module (e.g. `project :core:domain`), not an external package.
            continue
        match = DEP_RE.search(line)
        if not match:
            continue
        group, name, version = match.group("group"), match.group("name"), match.group("version")
        upgrade = UPGRADE_RE.search(line)
        if upgrade:
            version = upgrade.group(1)
        resolved[(group, name)] = version
    return resolved, failed


def to_cyclonedx(resolved: dict[tuple[str, str], str], component_name: str = "stratum") -> dict:
    components = []
    for (group, name), version in sorted(resolved.items()):
        purl = f"pkg:maven/{group}/{name}@{version}"
        components.append(
            {
                "type": "library",
                "group": group,
                "name": name,
                "version": version,
                "bom-ref": purl,
                "purl": purl,
            }
        )
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {
            "component": {
                "type": "application",
                "name": component_name,
                "bom-ref": f"pkg:maven/com.aether/{component_name}",
            }
        },
        "components": components,
    }


def main() -> None:
    if "--self-test" in sys.argv:
        run_self_test()
        return
    component_name = "stratum"
    for i, arg in enumerate(sys.argv):
        if arg == "--component-name" and i + 1 < len(sys.argv):
            component_name = sys.argv[i + 1]
    resolved, failed = parse_dependencies(sys.stdin.read())
    if failed:
        print(
            f"error: refusing to emit SBOM from a partial dependency tree "
            f"({len(failed)} unresolved dependencies):",
            file=sys.stderr,
        )
        for line in failed:
            print(f"  {line}", file=sys.stderr)
        sys.exit(1)
    if not resolved:
        print(
            "error: no dependencies resolved; refusing to emit an empty SBOM",
            file=sys.stderr,
        )
        sys.exit(1)
    json.dump(to_cyclonedx(resolved, component_name), sys.stdout, indent=2)
    sys.stdout.write("\n")


CLEAN_FIXTURE = """\
releaseRuntimeClasspath - Runtime classpath of '/release'.
+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.0
|    +--- org.jetbrains:annotations:13.0 -> 23.0.0
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 1.8.21 (c)
|    \\--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 1.8.21 (*)
+--- project :core:domain
|    +--- org.jetbrains.kotlin:kotlin-stdlib:2.4.0 (*)
|    \\--- javax.inject:javax.inject:1
"""

FAILED_FIXTURE = """\
releaseRuntimeClasspath - Runtime classpath of '/release'.
+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.0
+--- androidx.hilt:hilt-navigation-compose:1.3.0 FAILED
+--- com.example:broken-lib:2.0.0 FAILED
"""


def run_self_test() -> None:
    """Fixture test: arrows, duplicates, constraints, projects, FAILED, empty."""
    # Clean tree parses as before.
    resolved, failed = parse_dependencies(CLEAN_FIXTURE)
    assert not failed, failed
    assert resolved[("org.jetbrains.kotlin", "kotlin-stdlib")] == "2.4.0", resolved
    # Upgrade arrow wins over the declared version.
    assert resolved[("org.jetbrains", "annotations")] == "23.0.0", resolved
    # (c) constraints are not dependencies.
    assert ("org.jetbrains.kotlin", "kotlin-stdlib-jdk8") not in resolved, resolved
    # (*) duplicates are not re-emitted.
    assert ("org.jetbrains.kotlin", "kotlin-stdlib-jdk7") not in resolved, resolved
    # Local project modules are not external packages.
    assert not any(g == "project" for g, _ in resolved), resolved
    assert ("javax.inject", "javax.inject") in resolved, resolved

    sbom = to_cyclonedx(resolved, "stratum-test")
    assert sbom["bomFormat"] == "CycloneDX" and sbom["specVersion"] == "1.5"
    assert sbom["metadata"]["component"]["name"] == "stratum-test", sbom["metadata"]
    purls = {c["purl"] for c in sbom["components"]}
    assert "pkg:maven/org.jetbrains/annotations@23.0.0" in purls, purls
    clean_count = len(sbom["components"])

    # FAILED lines are reported, never silently dropped, and never enter the SBOM.
    resolved, failed = parse_dependencies(FAILED_FIXTURE)
    assert len(failed) == 2, failed
    assert any("hilt-navigation-compose" in line for line in failed), failed
    assert any("broken-lib" in line for line in failed), failed
    assert ("androidx.hilt", "hilt-navigation-compose") not in resolved, resolved
    assert ("com.example", "broken-lib") not in resolved, resolved
    sbom = to_cyclonedx(resolved)
    assert not any("hilt-navigation-compose" in c["purl"] for c in sbom["components"])

    # Empty input resolves nothing; main() must refuse it (no empty SBOM).
    resolved, failed = parse_dependencies("")
    assert resolved == {} and failed == [], (resolved, failed)

    print(f"self-test OK: {clean_count} components (clean fixture)")


if __name__ == "__main__":
    main()
