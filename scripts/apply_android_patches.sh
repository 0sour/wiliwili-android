#!/usr/bin/env bash
# Apply Android-specific patches to third-party submodules.
#
# The parent repository pins upstream submodule commits; these patches carry
# the small Android changes that are not upstream yet. The script is
# idempotent: it skips a patch that is already applied.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH_DIR="$ROOT/patches"

apply_patch() {
    local repo="$1"
    local patch="$2"
    local name
    name="$(basename "$patch")"

    if [ ! -d "$repo/.git" ] && [ ! -f "$repo/.git" ]; then
        echo "submodule missing: $repo" >&2
        echo "run: git submodule update --init --recursive" >&2
        exit 1
    fi

    if git -C "$repo" apply --reverse --check "$patch" 2>/dev/null; then
        echo "==> $name already applied"
        return 0
    fi

    if ! git -C "$repo" apply --check "$patch"; then
        echo "==> cannot apply $name; submodule is not in the expected state" >&2
        exit 1
    fi

    git -C "$repo" apply "$patch"
    echo "==> applied $name"
}

apply_patch "$ROOT/library/borealis" "$PATCH_DIR/borealis-android-logger.patch"
