#!/usr/bin/env python3
"""Upload a signed AAB to the Play Internal track and commit the release.

Usage: play-upload-internal.py <aab-path>

Auth: PLAY_PUBLISHER_JSON holds either a path to the service-account JSON
key (GitHub File-style secret materialized to a file) or the raw JSON. The
account needs Internal-track release rights for PACKAGE_NAME.

Release notes: the Play "what's new" text is read from
distribution/whatsnew/en-US.txt in this repo (WHATSNEW_PATH overrides the
location), checked against Play's 500-character cap before any edit is
opened, and attached to the release as its en-US notes.

Verification: after the edit commits, the Internal track is read back
through the Play API and the uploaded versionCode must be present as a
completed release carrying the same en-US notes. The rules this script
implements live in contracts/RELEASE.md.
"""

import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = os.environ.get("PACKAGE_NAME", "com.aether.stratum")
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
WHATSNEW_LANGUAGE = "en-US"
WHATSNEW_MAX_CHARS = 500


def default_whatsnew_path() -> str:
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(repo_root, "distribution", "whatsnew", "en-US.txt")


def load_release_notes() -> str:
    path = os.environ.get("WHATSNEW_PATH", default_whatsnew_path())
    with open(path, encoding="utf-8") as f:
        text = f.read().rstrip("\n")
    if not text:
        raise SystemExit(f"Release notes are empty: {path}")
    if len(text) > WHATSNEW_MAX_CHARS:
        raise SystemExit(
            f"Release notes are {len(text)} characters, over Play's "
            f"{WHATSNEW_MAX_CHARS}-character cap: {path}"
        )
    return text


def load_service_account_info() -> dict:
    ref = os.environ.get("PLAY_PUBLISHER_JSON", "")
    if not ref:
        raise SystemExit("PLAY_PUBLISHER_JSON is not set")
    if os.path.isfile(ref):
        with open(ref, encoding="utf-8") as f:
            return json.load(f)
    return json.loads(ref)


def main(aab_path: str) -> None:
    release_notes = load_release_notes()
    credentials = service_account.Credentials.from_service_account_info(
        load_service_account_info(), scopes=SCOPES
    )
    service = build("androidpublisher", "v3", credentials=credentials,
                    cache_discovery=False)
    edits = service.edits()
    edit_id = edits.insert(body={}, packageName=PACKAGE_NAME).execute()["id"]
    try:
        uploaded = edits.bundles().upload(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            media_body=MediaFileUpload(aab_path, mimetype="application/octet-stream"),
        ).execute()
        version_code = str(uploaded["versionCode"])
        edits.tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track="internal",
            body={"releases": [{
                "versionCodes": [version_code],
                "status": "completed",
                "releaseNotes": [{
                    "language": WHATSNEW_LANGUAGE,
                    "text": release_notes,
                }],
            }]},
        ).execute()
        edits.commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    except Exception:
        edits.delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        raise
    print(f"Uploaded versionCode {version_code} to the Internal track and committed.")

    # Verification read-back (contracts/RELEASE.md section 4): open a
    # throwaway edit, read the track through it, then delete the edit so
    # nothing is left open. The committed release is what gets read.
    verify_edit_id = edits.insert(body={}, packageName=PACKAGE_NAME).execute()["id"]
    try:
        track = edits.tracks().get(
            packageName=PACKAGE_NAME, editId=verify_edit_id, track="internal"
        ).execute()
    finally:
        edits.delete(packageName=PACKAGE_NAME, editId=verify_edit_id).execute()
    for release in track.get("releases", []):
        if version_code not in [str(c) for c in release.get("versionCodes", [])]:
            continue
        if release.get("status") != "completed":
            raise SystemExit(
                f"Verification failed: versionCode {version_code} is on the "
                f"Internal track with status {release.get('status')!r}, not 'completed'."
            )
        notes = {n.get("language"): n.get("text") for n in release.get("releaseNotes", [])}
        if notes.get(WHATSNEW_LANGUAGE) != release_notes:
            raise SystemExit(
                f"Verification failed: versionCode {version_code} is on the "
                f"Internal track but its {WHATSNEW_LANGUAGE} release notes do not "
                "match distribution/whatsnew/en-US.txt."
            )
        print(f"Verified versionCode {version_code} on the Internal track "
              f"with the checked-in {WHATSNEW_LANGUAGE} release notes.")
        return
    raise SystemExit(
        f"Verification failed: versionCode {version_code} was committed but "
        "does not appear on the Internal track."
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <aab-path>")
    main(sys.argv[1])
