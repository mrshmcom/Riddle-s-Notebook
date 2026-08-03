#!/usr/bin/env bash
# =============================================================================
# Riddle's Diary — Android Build Script (Docker)
#
# Usage:
#   ./build.sh              Build the APK using Docker
#   ./build.sh clean        Clean build artifacts
#   ./build.sh shell        Open a shell in the build container
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OUTPUT_DIR="$SCRIPT_DIR/output"
APK_PATH="$OUTPUT_DIR/app-debug.apk"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log()  { echo -e "${GREEN}[BUILD]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; }
info() { echo -e "${CYAN}[INFO]${NC} $*"; }

# ── Commands ──────────────────────────────────────────────────────

cmd_build() {
    log "Starting Riddle's Diary build in Docker..."
    mkdir -p "$OUTPUT_DIR"

    # Build the Docker image (this runs the full Android build)
    docker build \
        --build-arg BUILDKIT_INLINE_CACHE=1 \
        -t riddles-diary-builder \
        -f Dockerfile \
        .

    # Extract the APK
    log "Extracting APK from Docker image..."
    docker run --rm \
        -v "$OUTPUT_DIR:/output" \
        riddles-diary-builder \
        bash -c "cp /app/app/build/outputs/apk/debug/app-debug.apk /output/"

    if [ -f "$APK_PATH" ]; then
        local size=$(du -h "$APK_PATH" | cut -f1)
        echo ""
        log "═══════════════════════════════════════════════════════"
        log "  ✨ Build successful!"
        log "  📱 APK: output/app-debug.apk ($size)"
        log "  📋 Install on your Android device to begin."
        log "═══════════════════════════════════════════════════════"
    else
        err "APK not found at expected location. Build may have failed."
        exit 1
    fi
}

cmd_shell() {
    log "Opening shell in build environment..."
    docker compose run --rm shell
}

cmd_clean() {
    log "Cleaning build artifacts..."
    rm -rf "$OUTPUT_DIR"
    rm -rf .gradle
    rm -rf app/build
    docker compose down -v 2>/dev/null || true
    log "Clean complete."
}

# ── Main ──────────────────────────────────────────────────────────

case "${1:-build}" in
    build)
        cmd_build
        ;;
    shell)
        cmd_shell
        ;;
    clean)
        cmd_clean
        ;;
    *)
        echo "Usage: $0 {build|shell|clean}"
        exit 1
        ;;
esac
