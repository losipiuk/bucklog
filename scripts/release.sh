#!/usr/bin/env bash
# Publishes a new release: bumps versionCode (+1) and versionName in androidApp/build.gradle.kts,
# commits, tags v<version> and pushes. GitHub Actions then builds the signed APK and attaches it
# to a GitHub Release. Usage: scripts/release.sh 0.5.0
set -euo pipefail

version="${1:-}"
gradle_file="androidApp/build.gradle.kts"
cd "$(git rev-parse --show-toplevel)"

die() { echo "release: $*" >&2; exit 1; }

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: scripts/release.sh <major.minor.patch>, e.g. 0.5.0"
[[ "$(git branch --show-current)" == "main" ]] || die "switch to main first"
[[ -z "$(git status --porcelain)" ]] || die "commit or stash your changes first"
git fetch --quiet --tags origin
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || die "main isn't in sync with origin/main (pull or push first)"
! git rev-parse -q --verify "refs/tags/v$version" >/dev/null || die "tag v$version already exists"

old_code=$(perl -ne 'print $1 if /versionCode = (\d+)/' "$gradle_file")
old_name=$(perl -ne 'print $1 if /versionName = "([^"]+)"/' "$gradle_file")
[[ -n "$old_code" && -n "$old_name" ]] || die "couldn't find versionCode/versionName in $gradle_file"
new_code=$((old_code + 1))

perl -pi -e "s/versionCode = \d+/versionCode = $new_code/; s/versionName = \"[^\"]+\"/versionName = \"$version\"/" "$gradle_file"
echo "Version $old_name ($old_code) → $version ($new_code)"

git commit --quiet -am "Release v$version"
git tag -a "v$version" -m "Bucklog $version"
git push --quiet origin main "v$version"

echo "Pushed v$version. The APK will be attached to https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo losipiuk/bucklog)/releases/tag/v$version in about 7 minutes."
if command -v gh >/dev/null; then
    echo "Follow the build: gh run watch \$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')"
fi
