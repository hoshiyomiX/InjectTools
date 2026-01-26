#!/bin/bash
# Build Rust library for Android

set -e

echo "Building Rust library for Android..."

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if NDK is set
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo -e "${RED}Error: ANDROID_NDK_HOME not set${NC}"
    echo "Please set ANDROID_NDK_HOME to your Android NDK path"
    echo "Example: export ANDROID_NDK_HOME=~/android-ndk-r26d"
    exit 1
fi

echo -e "${GREEN}NDK Path: $ANDROID_NDK_HOME${NC}"

# Add NDK toolchains to PATH
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

# Navigate to project root
cd "$(dirname "$0")/.."

echo -e "${YELLOW}Building for ARM64 (aarch64-linux-android)...${NC}"
cargo build --release --lib --target aarch64-linux-android

echo -e "${YELLOW}Building for ARMv7 (armv7-linux-androideabi)...${NC}"
cargo build --release --lib --target armv7-linux-androideabi

echo -e "${GREEN}Build complete!${NC}"
echo ""
echo "Output files:"
echo "  - target/aarch64-linux-android/release/libinjecttools.so"
echo "  - target/armv7-linux-androideabi/release/libinjecttools.so"
echo ""
echo "Next step: Run ./android-app/copy-libs.sh to copy to jniLibs/"
