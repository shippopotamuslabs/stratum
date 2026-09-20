#!/usr/bin/env python3
"""Generate a CycloneDX 1.5 SBOM from Gradle dependency-tree output.

Usage:
    ./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q \\
        | python3 scripts/generate-sbom.py > bom.cdx.json

Reads the Gradle dependency tree from stdin, extracts resolved
``group:name:version`` triples (following ``->`` upgrade arrows, skipping
``(*)`` duplicate markers and ``(c)`` dependency-constraint markers), and
writes a CycloneDX 1.5 JSON SBOM with Maven purls to stdout.

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


def parse_dependencies(text: str) -> dict[tuple[str, str], str]:
    """Extract resolved (group, name) -> version from dependency-tree text."""
    resolved: dict[tuple[str, str], str] = {}
    for line in text.splitlines():
        if "(*)" in line or "(c)" in line:
            # (*)  = already listed above (omitted duplicate)
            # (c)  = dependency constraint, not an actual dependency
            continue
        if "FAILED" in line:
            # Unresolved dependency (e.g. network failure during resolution).
            # Its version is unknown, so it must not enter the SBOM.
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
    return resolved


def to_cyclonedx(resolved: dict[tuple[str, str], str]) -> dict:
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
                "name": "stratum",
                "bom-ref": "pkg:maven/com.aether/stratum",
            }
        },
        "components": components,
    }


def main() -> None:
    if "--self-test" in sys.argv:
        run_self_test()
        return
    resolved = parse_dependencies(sys.stdin.read())
    json.dump(to_cyclonedx(resolved), sys.stdout, indent=2)
    sys.stdout.write("\n")


FIXTURE = """\
releaseRuntimeClasspath - Runtime classpath of '/release'.
+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.0
|    +--- org.jetbrains:annotations:13.0 -> 23.0.0
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 1.8.21 (c)
|    \\--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 1.8.21 (*)
+--- project :core:domain
|    +--- org.jetbrains.kotlin:kotlin-stdlib:2.4.0 (*)
|    \\--- javax.inject:javax.inject:1
+--- androidx.hilt:hilt-navigation-compose:1.3.0 FAILED
+--- com.example:broken-lib:2.0.0 FAILED
"""


def run_self_test() -> None:
    """Fixture test: arrows, duplicates, constraints, projects, FAILED lines."""
    resolved = parse_dependencies(FIXTURE)
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
    # FAILED (unresolved) dependencies must not enter the SBOM.
    assert ("androidx.hilt", "hilt-navigation-compose") not in resolved, resolved
    assert ("com.example", "broken-lib") not in resolved, resolved

    sbom = to_cyclonedx(resolved)
    assert sbom["bomFormat"] == "CycloneDX" and sbom["specVersion"] == "1.5"
    purls = {c["purl"] for c in sbom["components"]}
    assert "pkg:maven/org.jetbrains/annotations@23.0.0" in purls, purls
    assert not any("hilt-navigation-compose" in p for p in purls), purls
    print(f"self-test OK: {len(sbom['components'])} components")


if __name__ == "__main__":
    main()
