#!/bin/zsh
# Package dist/Mastishka.app into dist/Mastishka-<version>.dmg with the classic
# drag-to-Applications layout. Run scripts/build-app.sh first (or this runs it for you).
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -d dist/Mastishka.app ]] || MASTISHKA_NO_LAUNCH=1 ./scripts/build-app.sh

VERSION=$(awk -F= '/^versionName=/{gsub(/[ \r]/,""); print $2}' version.properties)
DMG=dist/Mastishka-${VERSION}.dmg
STAGE=$(mktemp -d)

cp -R dist/Mastishka.app "$STAGE/"
ln -s /Applications "$STAGE/Applications"
rm -f "$DMG"
hdiutil create -volname "Mastishka ${VERSION}" -srcfolder "$STAGE" -ov -format UDZO "$DMG" >/dev/null
rm -rf "$STAGE"
echo "Wrote $DMG ($(du -h "$DMG" | cut -f1 | tr -d ' '))"
