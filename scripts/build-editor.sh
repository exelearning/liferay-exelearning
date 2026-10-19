#!/usr/bin/env bash
# Builds the static eXeLearning editor that is bundled into the
# org.exelearning.liferay.editor OSGi bundle.
#
# Variables:
#   EXELEARNING_EDITOR_REF       tag, branch or commit (default: .editor-version)
#   EXELEARNING_EDITOR_REF_TYPE  tag | branch | commit | release (default: tag)
#                                "release" downloads the official prebuilt
#                                exelearning-static-<tag>.zip release asset and
#                                verifies EXELEARNING_EDITOR_SHA256.
#   EXELEARNING_EDITOR_REPO_URL  upstream repository
#   EXELEARNING_EDITOR_SHA256    expected SHA-256 of the release asset
#   EXELEARNING_EDITOR_FORCE=1   rebuild even if the cached output matches
#
# Output: build/exelearning-editor/static and build/exelearning-editor/VERSION
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REF="${EXELEARNING_EDITOR_REF:-$(tr -d '[:space:]' < "$ROOT/.editor-version")}"
REF_TYPE="${EXELEARNING_EDITOR_REF_TYPE:-tag}"
REPO_URL="${EXELEARNING_EDITOR_REPO_URL:-https://github.com/exelearning/exelearning.git}"
OUT="$ROOT/build/exelearning-editor"
CACHE="$ROOT/.cache/exelearning-editor"

fail() {
	echo "build-editor: $*" >&2
	exit 1
}

case "$REF" in
	"" | *[!A-Za-z0-9._/-]*) fail "invalid EXELEARNING_EDITOR_REF '$REF'" ;;
esac

stamp="$REF_TYPE:$REF"

if [ "${EXELEARNING_EDITOR_FORCE:-0}" != "1" ] && [ -f "$OUT/static/index.html" ] &&
	[ -f "$OUT/VERSION" ] && head -n 1 "$OUT/VERSION" | grep -qxF "$stamp"; then
	echo "build-editor: $stamp already built ($(sed -n 2p "$OUT/VERSION"))"
	exit 0
fi

mkdir -p "$CACHE"
rm -rf "$OUT.tmp"
mkdir -p "$OUT.tmp"

if [ "$REF_TYPE" = "release" ]; then
	command -v curl >/dev/null || fail "curl is required"
	command -v unzip >/dev/null || fail "unzip is required"
	[ -n "${EXELEARNING_EDITOR_SHA256:-}" ] || fail "EXELEARNING_EDITOR_SHA256 is required for release assets"

	asset="exelearning-static-$REF.zip"
	repo_path="${REPO_URL#https://github.com/}"
	repo_path="${repo_path%.git}"
	zip="$CACHE/$asset"

	if [ ! -f "$zip" ]; then
		curl -fL --retry 3 -o "$zip.part" "https://github.com/$repo_path/releases/download/$REF/$asset"
		mv "$zip.part" "$zip"
	fi

	actual="$(shasum -a 256 "$zip" | cut -d' ' -f1)"
	[ "$actual" = "$EXELEARNING_EDITOR_SHA256" ] || fail "checksum mismatch for $asset: $actual"

	unzip -q "$zip" -d "$OUT.tmp"
	[ -d "$OUT.tmp/static" ] || fail "$asset has no static/ directory"
	for notice in LICENSE THIRD-PARTY-NOTICES.md; do
		curl -fsSL --retry 3 -o "$OUT.tmp/$notice" "https://raw.githubusercontent.com/$repo_path/$REF/$notice"
	done
	revision="sha256:$actual"
else
	command -v git >/dev/null || fail "git is required"
	command -v bun >/dev/null || fail "Bun is required to build the editor from source (https://bun.sh), or use EXELEARNING_EDITOR_REF_TYPE=release"

	src="$CACHE/src"

	if [ ! -d "$src/.git" ]; then
		rm -rf "$src"
		git init -q "$src"
		git -C "$src" remote add origin "$REPO_URL"
	fi

	git -C "$src" remote set-url origin "$REPO_URL"

	case "$REF_TYPE" in
		tag) git -C "$src" fetch -q --depth 1 origin "refs/tags/$REF:refs/tags/$REF" && git -C "$src" checkout -q --force "tags/$REF" ;;
		branch) git -C "$src" fetch -q --depth 1 origin "$REF" && git -C "$src" checkout -q --force FETCH_HEAD ;;
		commit)
			[[ "$REF" =~ ^[0-9a-f]{40}$ ]] || fail "commit refs must be full 40-character SHA-1 hashes"
			git -C "$src" fetch -q --depth 1 origin "$REF" && git -C "$src" checkout -q --force "$REF"
			;;
		*) fail "unknown EXELEARNING_EDITOR_REF_TYPE '$REF_TYPE'" ;;
	esac

	git -C "$src" clean -q -fdx -e node_modules
	revision="$(git -C "$src" rev-parse HEAD)"

	version="$REF"
	[ "$REF_TYPE" = "tag" ] || version="$REF-${revision:0:12}"

	(
		cd "$src"
		bun install --frozen-lockfile
		OUTPUT_DIR="$OUT.tmp/static" bun run build:static -- --version="$version"
	)
	[ -f "$OUT.tmp/static/index.html" ] || fail "the upstream build produced no index.html"
	cp "$src/LICENSE" "$OUT.tmp/LICENSE"
	if [ -f "$src/THIRD-PARTY-NOTICES.md" ]; then
		cp "$src/THIRD-PARTY-NOTICES.md" "$OUT.tmp/THIRD-PARTY-NOTICES.md"
	fi
fi

printf '%s\n%s\n' "$stamp" "$revision" > "$OUT.tmp/VERSION"
rm -rf "$OUT"
mv "$OUT.tmp" "$OUT"
echo "build-editor: built $stamp ($revision) in $OUT"
