#!/usr/bin/env bash
# Tag a deployed SHA as v<versionName> (contracts/RELEASE.md sections 4-5).
#
# Usage: tag-release.sh <versionName> <sha>
#
# The tag records the FIRST deploy of a versionName. Three cases:
#   - no tag yet             -> create it at <sha> and push it
#   - tag at <sha>           -> already recorded; nothing to do
#   - tag at a different SHA -> a same-version redeploy: leave the tag
#                               where it is, log both SHAs, succeed
# Tags are never moved and never force-pushed under the release contract.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <versionName> <sha>" >&2
  exit 1
fi

VERSION_NAME="$1"
DEPLOYED_SHA="$2"
TAG="v$VERSION_NAME"

if git rev-parse -q --verify "refs/tags/$TAG" > /dev/null; then
  EXISTING=$(git rev-list -n 1 "$TAG")
  if [ "$EXISTING" = "$DEPLOYED_SHA" ]; then
    echo "Tag $TAG already points at the deployed SHA — nothing to do"
  else
    echo "Tag $TAG already exists at $EXISTING (this version's first deploy); this run deployed $DEPLOYED_SHA under the same versionName. Tag left in place per contracts/RELEASE.md."
  fi
  exit 0
fi

git tag "$TAG" "$DEPLOYED_SHA"
git push origin "refs/tags/$TAG"
echo "Tagged $DEPLOYED_SHA as $TAG"
