#!/usr/bin/env bash
# Sets appVersion in gradle.properties, commits it, tags v<version> and pushes the commit and tag together,
# which starts the release workflow. Usage: scripts/release.sh 1.0.1-alpha
set -euo pipefail

die() {
    echo "release: $*" >&2
    exit 1
}

version=${1:-}
[[ -n $version ]] || die "usage: scripts/release.sh <version>, such as 1.0.1-alpha"
[[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z.]+)?$ ]] ||
    die "$version isn't MAJOR.MINOR.PATCH with an optional label, such as 1.0.1-alpha"
# The NSIS installer's limits.
((BASH_REMATCH[1] <= 255 && BASH_REMATCH[2] <= 255 && BASH_REMATCH[3] <= 65535)) ||
    die "Windows needs MAJOR and MINOR at most 255, and PATCH at most 65535"
numbers=${version%%-*}
tag=v$version

cd "$(git rev-parse --show-toplevel)"
branch=$(git symbolic-ref --quiet --short HEAD) || die "check out a branch first"
git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first"

git fetch --quiet --tags origin
git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null && die "$tag already exists"

# Packages only carry the numbers, so clients only update when they go up: 1.0.0-alpha then 1.0.0 looks the same.
latest=$(git tag --list 'v[0-9]*' | sed 's/^v//; s/-.*//' | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)
if [[ -n $latest ]]; then
    highest=$(printf '%s\n%s\n' "$latest" "$numbers" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)
    [[ $numbers != "$latest" && $highest == "$numbers" ]] ||
        die "$numbers must be higher than $latest, the last released version, or clients won't update"
fi

current=$(sed -n 's/^appVersion=//p' gradle.properties)
if [[ $current != "$version" ]]; then
    sed "s/^appVersion=.*/appVersion=$version/" gradle.properties >gradle.properties.tmp
    mv gradle.properties.tmp gradle.properties
    git add gradle.properties
    git commit --quiet -m "Release $tag"
fi
git tag --annotate "$tag" --message "Release $tag"

# Atomic, so the tag never reaches GitHub without the commit that sets its version.
if ! git push --atomic origin "$branch" "$tag"; then
    echo "release: the push failed, so nothing was published. To try again later: git push --atomic origin $branch $tag" >&2
    echo "release: or to undo locally: git tag -d $tag, and drop the release commit if one was made" >&2
    exit 1
fi
echo "Pushed $tag. Publish the draft release once all three OSes have uploaded."
