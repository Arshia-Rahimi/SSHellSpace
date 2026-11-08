#!/usr/bin/env bash
# Tags the current commit v<appVersion> (gradle.properties) and pushes only that tag, which starts the release
# workflow. Set appVersion and merge to main first, then run this on main. Usage: scripts/release.sh
set -euo pipefail

die() {
    echo "release: $*" >&2
    exit 1
}

cd "$(git rev-parse --show-toplevel)"

version=$(sed -n 's/^appVersion=//p' gradle.properties | tr -d '\r')
[[ -n $version ]] || die "gradle.properties has no appVersion"
[[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z.]+)?$ ]] ||
    die "appVersion $version isn't MAJOR.MINOR.PATCH with an optional label, such as 1.0.1-alpha"
# The NSIS installer's limits.
((BASH_REMATCH[1] <= 255 && BASH_REMATCH[2] <= 255 && BASH_REMATCH[3] <= 65535)) ||
    die "Windows needs MAJOR and MINOR at most 255, and PATCH at most 65535"
numbers=${version%%-*}
tag=v$version

# Only main, and only what's already on GitHub's main, so the release is a commit CI has checked.
[[ $(git symbolic-ref --quiet --short HEAD) == main ]] || die "check out main first"
git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first"
git fetch --quiet --tags origin main
[[ $(git rev-parse HEAD) == $(git rev-parse origin/main) ]] ||
    die "main doesn't match origin/main: push or pull first"

# GitHub's tags are what's been released. Local ones can be left over from a release deleted there.
remote_tags=$(git ls-remote --tags --refs origin 'v[0-9]*' | sed 's|.*refs/tags/||')
grep -qxF "$tag" <<<"$remote_tags" && die "$tag is already on GitHub"

# Packages only carry the numbers, so clients only update when they go up: 1.0.0-alpha then 1.0.0 looks the same.
latest=$(sed 's/^v//; s/-.*//' <<<"$remote_tags" | grep . | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1 || true)
if [[ -n $latest ]]; then
    highest=$(printf '%s\n%s\n' "$latest" "$numbers" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)
    [[ $numbers != "$latest" && $highest == "$numbers" ]] ||
        die "appVersion $numbers must be higher than $latest, the last released version, or clients won't update"
fi

if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
    echo "release: $tag is only in this clone, left from a release deleted on GitHub, so it moves to this commit"
    git tag --delete "$tag" >/dev/null
fi
git tag --annotate "$tag" --message "Release $tag"
if ! git push origin "refs/tags/$tag"; then
    git tag --delete "$tag" >/dev/null
    die "the push failed, so the tag was removed again. Nothing was published"
fi
echo "Pushed $tag. Publish the draft release once all three OSes have uploaded."
