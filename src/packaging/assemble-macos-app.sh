#!/bin/bash
# Assembles a macOS .app bundle from a GraalVM native-image binary.
#
# Usage: ./assemble-macos-app.sh <path-to-native-binary> [output-dir]
#
# Example:
#   ./assemble-macos-app.sh target/OpenGGF dist

set -euo pipefail

# Only assemble .app on macOS; silently skip on other platforms
if [ "$(uname)" != "Darwin" ]; then
    echo "Skipping .app assembly (not macOS)"
    exit 0
fi

BINARY="${1:?Usage: $0 <path-to-native-binary> [output-dir]}"
OUTPUT_DIR="${2:-.}"
APP_NAME="OpenGGF"
APP_BUNDLE="${OUTPUT_DIR}/${APP_NAME}.app"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Clean previous bundle
rm -rf "${APP_BUNDLE}"

# Create directory structure
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"

# Copy Info.plist
cp "${SCRIPT_DIR}/Info.plist" "${APP_BUNDLE}/Contents/"

# Copy native binary as OpenGGF.bin
cp "${BINARY}" "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}.bin"
chmod +x "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}.bin"

# Copy LWJGL native libraries (extracted by Maven build)
NATIVE_LIBS_DIR="$(dirname "${BINARY}")/native-libs"
if [ -d "${NATIVE_LIBS_DIR}" ]; then
    cp "${NATIVE_LIBS_DIR}"/*.dylib "${APP_BUNDLE}/Contents/MacOS/" 2>/dev/null || true
    echo "Bundled native libraries from ${NATIVE_LIBS_DIR}"
fi

# Create launcher script as the CFBundleExecutable.
# Handles two GraalVM native-image issues on macOS:
# 1. SONIC_NATIVE_LIBS_DIR: custom env var for LWJGL library discovery
#    (macOS SIP strips DYLD_LIBRARY_PATH from Finder-launched processes)
# 2. -Duser.dir: GraalVM's getcwd() fails when launched via Finder/open,
#    so we explicitly set user.dir for property init and file path resolution
cat > "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}" << 'LAUNCHER'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
# Set working directory to the folder containing the .app bundle,
# so the engine finds the ROM file placed next to OpenGGF.app
APP_DIR="$(cd "${DIR}/../../.." && pwd)"
cd "${APP_DIR}"

# Custom env var for LWJGL native lib discovery (not stripped by SIP)
export SONIC_NATIVE_LIBS_DIR="${DIR}"
# Also set DYLD_LIBRARY_PATH as fallback (works from Terminal, stripped by Finder)
export DYLD_LIBRARY_PATH="${DIR}${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"

# GraalVM native-image's getcwd() fails when launched via macOS
# LaunchServices (Finder/open). -Duser.dir bypasses the broken
# getcwd() in property initialization and file path resolution.
"${DIR}/OpenGGF.bin" "-Duser.dir=${APP_DIR}" "$@"
LAUNCHER
chmod +x "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"

# Copy icon if available
if [ -f "${SCRIPT_DIR}/${APP_NAME}.icns" ]; then
    cp "${SCRIPT_DIR}/${APP_NAME}.icns" "${APP_BUNDLE}/Contents/Resources/"
fi

echo "Created ${APP_BUNDLE}"
