#!/usr/bin/env bash
# build-ipa.sh — Build an unsigned FlutLink iOS IPA
# TEST — iOS port created by opencode. Not a production build.
#
# This script:
#   1. Generates the Xcode project from project.yml (via XcodeGen), if needed
#   2. Builds an unsigned .app archive
#   3. Packages it into an unsigned .ipa
#
# Prerequisites:
#   - macOS with Xcode 16+ and command-line tools
#   - XcodeGen: brew install xcodegen  (skipped if .xcodeproj already exists)
#   - (Optional) FLUTCLOUD_URL env var to lock the server URL
#
# Usage:
#   ./scripts/build-ipa.sh [--release] [--skip-generate]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
IOS_DIR="$REPO_ROOT/ios"
BUILD_DIR="$REPO_ROOT/build/ios"
ARCHIVE_PATH="$BUILD_DIR/FlutLink.xcarchive"
EXPORT_PATH="$BUILD_DIR/export"
IPA_PATH="$BUILD_DIR/FlutLink.ipa"

CONFIG="Debug"
SKIP_GENERATE=false
for arg in "$@"; do
    case "$arg" in
        --release) CONFIG="Release" ;;
        --skip-generate) SKIP_GENERATE=true ;;
    esac
done

echo "=== FlutLink iOS IPA Builder ==="
echo "TEST — created by opencode"
echo ""

# Step 1: Generate Xcode project (only if .xcodeproj is missing or not skipped)
if [[ "$SKIP_GENERATE" == false && ! -d "$IOS_DIR/FlutLink.xcodeproj" ]]; then
    if ! command -v xcodegen &>/dev/null; then
        echo "[!] XcodeGen not found. Install it with: brew install xcodegen"
        echo "    Or pass --skip-generate if the project already exists."
        exit 1
    fi
    echo "[1/3] Generating Xcode project..."
    cd "$IOS_DIR"
    xcodegen generate --spec project.yml
    echo "      Done."
else
    echo "[1/3] Skipping Xcode project generation (already exists or --skip-generate)."
fi

# Step 2: Build archive
echo "[2/3] Building $CONFIG archive..."
mkdir -p "$BUILD_DIR"

xcodebuild \
    -project "$IOS_DIR/FlutLink.xcodeproj" \
    -scheme FlutLink \
    -configuration "$CONFIG" \
    -archivePath "$ARCHIVE_PATH" \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$BUILD_DIR/DerivedData" \
    CODE_SIGN_IDENTITY="" \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGNING_ALLOWED=NO \
    AD_HOC_CODE_SIGNING_ALLOWED=YES \
    ENABLE_BITCODE=NO \
    archive 2>&1 | tail -20
echo "      Archive created at $ARCHIVE_PATH"

# Step 3: Package into IPA (directly from archive .app — no export needed for unsigned builds)
echo "[3/3] Packaging IPA..."
mkdir -p "$IPA_PATH/Payload"

APP_PATH=$(find "$ARCHIVE_PATH/Products/Applications" -name "*.app" -maxdepth 1 | head -1)
if [[ -z "$APP_PATH" ]]; then
    echo "[!] ERROR: No .app found in archive."
    echo "    Archive contents:"
    find "$ARCHIVE_PATH" -name "*.app" 2>/dev/null
    exit 1
fi

cp -R "$APP_PATH" "$IPA_PATH/Payload/"
cd "$IPA_PATH"
zip -r -q "$BUILD_DIR/FlutLink.ipa" Payload/
cd "$REPO_ROOT"
rm -rf "$IPA_PATH"

echo ""
echo "=== Done ==="
echo "Unsigned IPA: $BUILD_DIR/FlutLink.ipa"
echo "Install with: AltStore, Sideloadly, or similar tool."
echo ""
echo "NOTE: This is an unsigned IPA. A signing identity is required to install."
echo "TEST — iOS port created by opencode. Not a production build."
