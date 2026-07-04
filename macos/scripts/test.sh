#!/bin/zsh
# Run unit tests with Command Line Tools only (no Xcode) — same trick as vishrama:
# CLT ships Testing.framework outside the default search path, and its
# _Testing_Foundation cross-import overlay has no swiftmodule — so we add the
# framework path explicitly and disable cross-import overlays.
set -euo pipefail
cd "$(dirname "$0")/.."

FWK=/Library/Developer/CommandLineTools/Library/Developer/Frameworks
exec swift test \
    -Xswiftc -F$FWK \
    -Xswiftc -Xfrontend -Xswiftc -disable-cross-import-overlays \
    -Xlinker -F$FWK \
    -Xlinker -rpath -Xlinker $FWK \
    "$@"
