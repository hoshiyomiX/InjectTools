#!/bin/bash
# InjectTools - Smart Installer for Termux
# Usage: curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}   InjectTools v2.3 - Smart Installer${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Detect architecture
ARCH=$(uname -m)
echo -e "${BLUE}📱 Detected architecture: ${GREEN}$ARCH${NC}"

if [[ "$ARCH" == "aarch64" ]]; then
    BINARY="injecttools-termux-arm64"
    RUST_TARGET="aarch64-linux-android"
    echo -e "${GREEN}✓ Using ARM64 binary (modern devices)${NC}"
elif [[ "$ARCH" == "armv7"* ]] || [[ "$ARCH" == "armv8l" ]]; then
    BINARY="injecttools-termux-armv7"
    RUST_TARGET="armv7-linux-androideabi"
    echo -e "${GREEN}✓ Using ARMv7 binary (older devices)${NC}"
else
    echo -e "${RED}✗ Unsupported architecture: $ARCH${NC}"
    echo -e "${YELLOW}  Supported: aarch64 (ARM64), armv7/armv8l (ARMv7)${NC}"
    exit 1
fi

echo ""

# Check if running in Termux
if [[ -z "$PREFIX" ]]; then
    echo -e "${RED}✗ Error: Not running in Termux${NC}"
    echo -e "${YELLOW}  This installer is designed for Termux on Android${NC}"
    exit 1
fi

# Try to download pre-built binary first
echo -e "${CYAN}🔍 Checking for pre-built release...${NC}"

# Try multiple version tags
VERSIONS=("termux-v2.3.0" "v2.3.0" "termux-v1.1.0" "v1.1.0")
DOWNLOAD_SUCCESS=false

for VERSION in "${VERSIONS[@]}"; do
    BASE_URL="https://github.com/hoshiyomiX/InjectTools/releases/download/$VERSION"
    TARBALL="${BINARY}.tar.gz"
    DOWNLOAD_URL="$BASE_URL/$TARBALL"
    
    echo -e "${BLUE}  Trying version: ${CYAN}$VERSION${NC}"
    
    TMP_DIR=$(mktemp -d)
    cd "$TMP_DIR"
    
    if curl -fsSL -o "$TARBALL" "$DOWNLOAD_URL" 2>/dev/null; then
        echo -e "${GREEN}✓ Found release: $VERSION${NC}"
        
        # Extract
        echo -e "${CYAN}📎 Extracting...${NC}"
        if tar xzf "$TARBALL" 2>/dev/null && [[ -f "injecttools" ]]; then
            # Backup existing
            if [[ -f "$PREFIX/bin/injecttools" ]]; then
                echo -e "${YELLOW}⚠ Backing up existing installation...${NC}"
                mv "$PREFIX/bin/injecttools" "$PREFIX/bin/injecttools.backup.$(date +%s)"
            fi
            
            # Install
            echo -e "${CYAN}📦 Installing...${NC}"
            mv injecttools "$PREFIX/bin/"
            chmod +x "$PREFIX/bin/injecttools"
            
            # Cleanup
            cd ~
            rm -rf "$TMP_DIR"
            
            DOWNLOAD_SUCCESS=true
            break
        fi
    fi
    
    # Cleanup failed attempt
    cd ~
    rm -rf "$TMP_DIR"
done

if [[ "$DOWNLOAD_SUCCESS" == "true" ]]; then
    echo -e "${GREEN}✓ Pre-built binary installed${NC}"
else
    # No release found, build from source
    echo -e "${YELLOW}⚠ No pre-built release found${NC}"
    echo -e "${CYAN}🔨 Building from source...${NC}"
    echo ""
    
    # Check dependencies
    MISSING=()
    
    if ! command -v git &> /dev/null; then
        MISSING+=("git")
    fi
    
    if ! command -v rustc &> /dev/null; then
        MISSING+=("rust")
    fi
    
    if ! command -v pkg-config &> /dev/null; then
        MISSING+=("binutils")
    fi
    
    if [[ ${#MISSING[@]} -gt 0 ]]; then
        echo -e "${YELLOW}📦 Installing build dependencies...${NC}"
        for pkg in "${MISSING[@]}"; do
            echo -e "${CYAN}  Installing $pkg...${NC}"
            pkg install -y $pkg
        done
        echo -e "${GREEN}✓ Dependencies installed${NC}"
        echo ""
    fi
    
    # Clone repo
    BUILD_DIR="$HOME/InjectTools-build"
    if [[ -d "$BUILD_DIR" ]]; then
        echo -e "${YELLOW}⚠ Removing old build directory...${NC}"
        rm -rf "$BUILD_DIR"
    fi
    
    echo -e "${CYAN}📥 Cloning repository...${NC}"
    if ! git clone --depth 1 https://github.com/hoshiyomiX/InjectTools.git "$BUILD_DIR"; then
        echo -e "${RED}✗ Failed to clone repository${NC}"
        exit 1
    fi
    
    cd "$BUILD_DIR"
    
    # Build
    echo -e "${CYAN}🔨 Compiling Rust code...${NC}"
    echo -e "${YELLOW}  This may take 5-15 minutes depending on your device${NC}"
    echo ""
    
    if cargo build --release --target "$RUST_TARGET" 2>&1 | grep -E '(Compiling|Finished|error)'; then
        BINARY_PATH="target/$RUST_TARGET/release/injecttools"
        
        if [[ -f "$BINARY_PATH" ]]; then
            # Backup existing
            if [[ -f "$PREFIX/bin/injecttools" ]]; then
                echo -e "${YELLOW}⚠ Backing up existing installation...${NC}"
                mv "$PREFIX/bin/injecttools" "$PREFIX/bin/injecttools.backup.$(date +%s)"
            fi
            
            # Install
            echo -e "${CYAN}📦 Installing...${NC}"
            cp "$BINARY_PATH" "$PREFIX/bin/injecttools"
            chmod +x "$PREFIX/bin/injecttools"
            
            echo -e "${GREEN}✓ Built from source successfully${NC}"
        else
            echo -e "${RED}✗ Build succeeded but binary not found${NC}"
            exit 1
        fi
    else
        echo -e "${RED}✗ Build failed${NC}"
        echo -e "${YELLOW}  Check error messages above${NC}"
        exit 1
    fi
    
    # Cleanup
    cd ~
    echo -e "${CYAN}🧹 Cleaning up build directory...${NC}"
    rm -rf "$BUILD_DIR"
fi

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Installation Complete!${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}📍 Binary location: ${GREEN}$PREFIX/bin/injecttools${NC}"
if [[ -f "$PREFIX/bin/injecttools" ]]; then
    echo -e "${BLUE}📏 Binary size: ${GREEN}$(du -h $PREFIX/bin/injecttools | cut -f1)${NC}"
fi
echo ""
echo -e "${YELLOW}🚀 Run the tool:${NC}"
echo -e "   ${CYAN}injecttools${NC}"
echo ""
echo -e "${YELLOW}📚 Help & Options:${NC}"
echo -e "   ${CYAN}injecttools --help${NC}"
echo ""
echo -e "${BLUE}Created by: ${CYAN}t.me/hoshiyomi_id${NC}"
echo ""

# Test binary
if injecttools --version &>/dev/null; then
    VERSION_OUTPUT=$(injecttools --version 2>&1)
    echo -e "${GREEN}✓ Installation verified: $VERSION_OUTPUT${NC}"
else
    echo -e "${YELLOW}⚠ Binary installed but verification failed${NC}"
    echo -e "${YELLOW}  Try running: ${CYAN}injecttools --version${NC}"
fi

echo ""