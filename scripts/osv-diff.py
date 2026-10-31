#!/usr/bin/env python3
"""Fail on OSV findings that a PR newly introduces (STR-277 diff-only scan).

Usage: osv-diff.py <baseline.json> <pr.json>

Both inputs are `osv-scanner scan source --format=json` results: the
baseline from the PR's base-branch tree, the other from the PR tree.
A finding's identity is the (vulnerability ID, package name) pair, so a
version bump that keeps the same advisory on the same package is not
"new", while a new advisory — or an advisory hitting a package that was
clean at baseline — is. Aliases (e.g. a CVE id for a GHSA record) count
as the same finding when either side reports them.

Exit codes: 0 = no new findings, 1 = new findings (listed on stdout),
2 = an input was missing or unparseable. The comparison fails closed on
bad input so a broken scan can never look like a clean one.
"""

import json
import sys


def load_findings(path):
    """Return {(vuln_id, package_name): detail} plus per-package alias ids."""
    try:
        with open(path, encoding="utf-8") as fh:
            doc = json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"osv-diff: cannot read scan result {path}: {exc}", file=sys.stderr)
        sys.exit(2)
    findings = {}
    package_ids = {}
    for result in doc.get("results", []):
        source = result.get("source", {}).get("path", "?")
        for entry in result.get("packages", []):
            package = entry.get("package", {})
            name = package.get("name", "?")
            version = package.get("version", "?")
            ecosystem = package.get("ecosystem", "")
            vulns = entry.get("vulnerabilities") or []
            if not vulns:
                # Some scanner versions report ids only via groups.
                vulns = [
                    {"id": gid}
                    for group in entry.get("groups", [])
                    for gid in group.get("ids", [])
                ]
            for vuln in vulns:
                vid = vuln.get("id")
                if not vid:
                    continue
                ids = {vid, *vuln.get("aliases", [])}
                package_ids.setdefault(name, set()).update(ids)
                findings.setdefault(
                    (vid, name),
                    {
                        "version": version,
                        "ecosystem": ecosystem,
                        "source": source,
                        "ids": ids,
                    },
                )
    return findings, package_ids


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    baseline, baseline_pkg_ids = load_findings(argv[1])
    current, _ = load_findings(argv[2])
    print(
        f"osv-diff: baseline findings: {len(baseline)}, "
        f"PR findings: {len(current)} (unique (vuln, package) pairs)"
    )
    new = []
    for (vid, name), detail in sorted(current.items()):
        if (vid, name) in baseline:
            continue
        # Same advisory under an alias id on a package already flagged
        # for it at baseline is not new either.
        if detail["ids"] & baseline_pkg_ids.get(name, set()):
            continue
        new.append((vid, name, detail))
    if not new:
        print("osv-diff: no new vulnerabilities introduced.")
        return 0
    print(f"osv-diff: {len(new)} NEW finding(s) introduced by this PR:")
    for vid, name, detail in new:
        print(
            f"  NEW {vid}  {detail['ecosystem']}/{name}@{detail['version']}"
            f"  (source: {detail['source']})"
            f"  https://osv.dev/vulnerability/{vid}"
        )
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
