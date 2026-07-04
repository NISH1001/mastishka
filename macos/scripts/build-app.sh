#!/bin/zsh
# Build mastishka-mac with SwiftPM, assemble dist/Mastishka.app, sign, and relaunch.
# No Xcode required — Command Line Tools only. (Same approach as vishrama's build-app.sh.)
set -euo pipefail
cd "$(dirname "$0")/.."

swift build -c release

APP=dist/Mastishka.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp .build/release/Mastishka "$APP/Contents/MacOS/Mastishka"
# SwiftPM resource bundle (the gong m4a renders) must ship inside the app.
cp -R .build/release/mastishka_Mastishka.bundle "$APP/Contents/Resources/"
# App icon (◎ on the night gradient — regenerate with ../scripts/generate_mac_icon.py).
cp assets/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"

VERSION=$(awk -F= '/^versionName=/{gsub(/[ \r]/,""); print $2}' version.properties)
BUILD_NUM=$(git rev-list --count HEAD 2>/dev/null || echo 0)
cat > "$APP/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>dev.nishparadox.mastishka</string>
    <key>CFBundleExecutable</key>
    <string>Mastishka</string>
    <key>CFBundleName</key>
    <string>Mastishka</string>
    <key>CFBundleDisplayName</key>
    <string>Mastishka</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundleVersion</key>
    <string>${BUILD_NUM}</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>CFBundleURLTypes</key>
    <array>
        <dict>
            <key>CFBundleURLName</key>
            <string>dev.nishparadox.mastishka</string>
            <key>CFBundleURLSchemes</key>
            <array>
                <string>mastishka</string>
            </array>
        </dict>
    </array>
</dict>
</plist>
EOF

# Prefer a stable self-signed identity (create one named "MastishkaDev" in
# Keychain Access to keep grants across rebuilds); fall back to ad-hoc.
IDENTITY=$(security find-identity -v -p codesigning 2>/dev/null | awk -F'"' '/MastishkaDev/{print $2; exit}')
codesign --force --sign "${IDENTITY:--}" "$APP"

pkill -x Mastishka 2>/dev/null || true
if [[ "${MASTISHKA_NO_LAUNCH:-0}" != "1" ]]; then
    open "$APP"
fi
echo "Built and launched $APP (v${VERSION} build ${BUILD_NUM}, identity: ${IDENTITY:-ad-hoc})"
