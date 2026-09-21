#!/usr/bin/env python3
"""Upload a signed AAB to the Play Internal track and commit the release.

Usage: play-upload-internal.py <aab-path>

Auth: PLAY_PUBLISHER_JSON holds either a path to the service-account JSON key
(GitLab File variable) or the raw JSON. The account needs Internal-track
release rights for PACKAGE_NAME.
"""

import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = os.environ.get("PACKAGE_NAME", "com.aether.stratum")
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def load_service_account_info() -> dict:
    ref = os.environ.get("PLAY_PUBLISHER_JSON", "")
    if not ref:
        raise SystemExit("PLAY_PUBLISHER_JSON is not set")
    if os.path.isfile(ref):
        with open(ref, encoding="utf-8") as f:
            return json.load(f)
    return json.loads(ref)


def main(aab_path: str) -> None:
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
            body={"releases": [{"versionCodes": [version_code],
                                "status": "completed"}]},
        ).execute()
        edits.commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    except Exception:
        edits.delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        raise
    print(f"Uploaded versionCode {version_code} to the Internal track and committed.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <aab-path>")
    main(sys.argv[1])
