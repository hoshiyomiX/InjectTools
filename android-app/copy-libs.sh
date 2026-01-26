#!/bin/bash
# Copy Rust .so libraries to Android jniLibs/

set -e

echo "Copying native libraries to jniLibs/..."

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Navigate to project root
cd "$(dirname "$0")/.."

# Create jniLibs directories
mkdir -p android-app/app/src/main/jniLibs/arm64-v8a
mkdir -p android-app/app/src/main/jniLibs/armeabi-v7a

# Copy ARM64
if [ -f "target/aarch64-linux-android/release/libinjecttools.so" ]; then
    echo -e "${YELLOW}Copying ARM64 library...${NC}"
    cp target/aarch64-linux-android/release/libinjecttools.so \
       android-app/app/src/main/jniLibs/arm64-v8a/
    echo -e "${GREEN}✓ ARM64 copied${NC}"
else
    echo "Warning: ARM64 library not found. Run build-rust.sh first."
fi

# Copy ARMv7
if [ -f "target/armv7-linux-androideabi/release/libinjecttools.so" ]; then
    echo -e "${YELLOW}Copying ARMv7 library...${NC}"
    cp target/armv7-linux-androideabi/release/libinjecttools.so \
       android-app/app/src/main/jniLibs/armeabi-v7a/
    echo -e "${GREEN}✓ ARMv7 copied${NC}"
else
    echo "Warning: ARMv7 library not found. Run build-rust.sh first."
fi

echo ""
echo -e "${GREEN}Done! Libraries copied to jniLibs/${NC}"
echo ""
echo "File sizes:"
du -h android-app/app/src/main/jniLibs/arm64-v8a/libinjecttools.so 2>/dev/null || echo "  ARM64: not found"
du -h android-app/app/src/main/jniLibs/armeabi-v7a/libinjecttools.so 2>/dev/null || echo "  ARMv7: not found"
echo ""
echo "Next step: cd android-app && ./gradlew assembleDebug"
