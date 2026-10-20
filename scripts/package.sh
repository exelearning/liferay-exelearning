#!/usr/bin/env bash
# Builds the installable release: make package VERSION=x.y.z
#
# Produces dist/liferay-exelearning-<version>.zip with the two OSGi bundles,
# installation notes, licenses and version metadata, plus SHA-256 checksums.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-}"
NAME="liferay-exelearning-$VERSION"
DIST="$ROOT/dist"
STAGE="$DIST/$NAME"

fail() {
	echo "package: $*" >&2
	exit 1
}

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || fail "VERSION must be semantic (x.y.z), got '$VERSION'"

# The repository keeps the placeholder Bundle-Version 0.0.0; the release stamps
# the version without pre-release suffix (OSGi versions have no "-") for this
# build only and restores the bnd files afterwards.
backup="$(mktemp -d)"
trap 'cp "$backup/exelearning-web.bnd" "$ROOT/modules/exelearning-web/bnd.bnd"; cp "$backup/exelearning-editor.bnd" "$ROOT/modules/exelearning-editor/bnd.bnd"; rm -rf "$backup"' EXIT
for module in exelearning-web exelearning-editor; do
	bnd="$ROOT/modules/$module/bnd.bnd"
	cp "$bnd" "$backup/$module.bnd"
	sed "s/^Bundle-Version: .*/Bundle-Version: ${VERSION%%-*}/" "$backup/$module.bnd" > "$bnd"
done

cd "$ROOT"
scripts/build-editor.sh
npm run build --silent
scripts/gradlew.sh --console=plain -q \
	:modules:exelearning-editor:clean :modules:exelearning-web:clean \
	:modules:exelearning-editor:jar :modules:exelearning-web:jar

web_jar="modules/exelearning-web/build/libs/org.exelearning.liferay.web-${VERSION%%-*}.jar"
editor_jar="modules/exelearning-editor/build/libs/org.exelearning.liferay.editor-${VERSION%%-*}.jar"
[ -f "$web_jar" ] || fail "missing $web_jar"
[ -f "$editor_jar" ] || fail "missing $editor_jar"

# Verify the bundles before shipping them.
manifest() {
	unzip -p "$1" META-INF/MANIFEST.MF | tr -d '\r' | sed -e ':a' -e 'N' -e '$!ba' -e 's/\n //g'
}
grep -qx "Bundle-SymbolicName: org.exelearning.liferay.web" <<<"$(manifest "$web_jar")" || fail "web bundle manifest"
grep -qx "Bundle-SymbolicName: org.exelearning.liferay.editor" <<<"$(manifest "$editor_jar")" || fail "editor bundle manifest"
# Listings are captured first: with pipefail, "unzip | grep -q" fails when grep
# exits early and unzip gets SIGPIPE.
web_files="$(unzip -l "$web_jar")"
editor_files="$(unzip -l "$editor_jar")"
grep -q "META-INF/resources/js/exelearning.js" <<<"$web_files" || fail "web bundle lacks the frontend"
grep -q "content/Language_es.properties" <<<"$web_files" || fail "web bundle lacks translations"
grep -q " static/index.html$" <<<"$editor_files" || fail "editor bundle lacks the static editor"
grep -q "META-INF/exelearning-editor/LICENSE" <<<"$editor_files" || fail "editor bundle lacks the editor license"
if grep -E '\.env$|node_modules/|tests/fixtures|\.elpx$|\.git/' <<<"$web_files$editor_files" >/dev/null; then
	fail "a bundle contains development files"
fi

rm -rf "$STAGE" "$DIST/$NAME.zip" "$DIST/$NAME.zip.sha256"
mkdir -p "$STAGE"
cp "$web_jar" "$STAGE/org.exelearning.liferay.web-$VERSION.jar"
cp "$editor_jar" "$STAGE/org.exelearning.liferay.editor-$VERSION.jar"
cp LICENSE CHANGELOG.md THIRD-PARTY-NOTICES.md SECURITY.md "$STAGE/"
cp docs/installation.md "$STAGE/INSTALL.md"
cp build/exelearning-editor/LICENSE "$STAGE/EXELEARNING-EDITOR-LICENSE"
if [ -f build/exelearning-editor/THIRD-PARTY-NOTICES.md ]; then
	cp build/exelearning-editor/THIRD-PARTY-NOTICES.md "$STAGE/EXELEARNING-EDITOR-THIRD-PARTY-NOTICES.md"
fi
{
	echo "version=$VERSION"
	echo "editor.ref=$(sed -n 1p build/exelearning-editor/VERSION)"
	echo "editor.revision=$(sed -n 2p build/exelearning-editor/VERSION)"
	echo "liferay.product=$(sed -n 's/^liferay.workspace.product=//p' gradle.properties)"
	echo "source.revision=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
} > "$STAGE/VERSION"

(
	cd "$STAGE"
	shasum -a 256 -- *.jar > SHA256SUMS
)

# Deterministic archive: sorted entries and a fixed timestamp.
find "$STAGE" -exec touch -t 202001010000 {} +
(
	cd "$DIST"
	find "$NAME" -type f | LC_ALL=C sort | zip -q -X -D "$NAME.zip" -@
	shasum -a 256 "$NAME.zip" > "$NAME.zip.sha256"
)

if unzip -l "$DIST/$NAME.zip" | grep -E '\.env|node_modules|tests/' >/dev/null; then
	fail "the release archive contains development files"
fi

echo "package: built $DIST/$NAME.zip"
unzip -l "$DIST/$NAME.zip"
cat "$DIST/$NAME.zip.sha256"
