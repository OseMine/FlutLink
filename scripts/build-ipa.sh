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
    echo "[1/4] Generating Xcode project..."
    cd "$IOS_DIR"
    xcodegen generate --spec project.yml
    echo "      Done."
else
    echo "[1/4] Skipping Xcode project generation (already exists or --skip-generate)."
fi

# Step 2: Build archive
echo "[2/4] Building $CONFIG archive..."
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
    archive 2>&1 | tail -5
echo "      Archive created at $ARCHIVE_PATH"

# Step 3: Export archive (unsigned / ad-hoc)
echo "[3/4] Exporting archive..."
mkdir -p "$EXPORT_PATH"

cat > "$BUILD_DIR/ExportOptions.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>ad-hoc</string>
    <key>signingStyle</key>
    <string>manual</string>
    <key>signingCertificate</key>
    <string></string>
    <key>compileBitcode</key>
    <false/>
</dict>
</plist>
PLIST

xcodebuild \
    -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportOptionsPlist "$BUILD_DIR/ExportOptions.plist" \
    -exportPath "$EXPORT_PATH" \
    CODE_SIGN_IDENTITY="" \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGNING_ALLOWED=NO \
    2>&1 | tail -3

# Step 4: Package into IPA
echo "[4/4] Packaging IPA..."
mkdir -p "$IPA_PATH/Payload"

APP_PATH=$(find "$EXPORT_PATH" -name "*.app" -maxdepth 1 | head -1)
if [[ -z "$APP_PATH" ]]; then
    echo "[!] ERROR: No .app found in export path."
    echo "    Export contents:"
    ls -la "$EXPORT_PATH/"
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
