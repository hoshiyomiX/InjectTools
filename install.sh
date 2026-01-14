#!/bin/bash
# InjectTools - One-liner Installer for Termux
# Usage: curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}   InjectTools Installer for Termux${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Detect architecture
ARCH=$(uname -m)
echo -e "${BLUE}📱 Detected architecture: ${GREEN}$ARCH${NC}"

if [[ "$ARCH" == "aarch64" ]]; then
    BINARY="injecttools-termux-arm64"
    echo -e "${GREEN}✓ Using ARM64 binary (modern devices)${NC}"
elif [[ "$ARCH" == "armv7"* ]] || [[ "$ARCH" == "armv8l" ]]; then
    BINARY="injecttools-termux-armv7"
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

# Version
VERSION="${1:-termux-v1.1.0}"
echo -e "${BLUE}📦 Installing version: ${GREEN}$VERSION${NC}"
echo ""

# Download URL
BASE_URL="https://github.com/hoshiyomiX/InjectTools/releases/download/$VERSION"
TARBALL="${BINARY}.tar.gz"
DOWNLOAD_URL="$BASE_URL/$TARBALL"
CHECKSUM_URL="$BASE_URL/$TARBALL.sha256"

# Create temp directory
TMP_DIR=$(mktemp -d)
cd "$TMP_DIR"

echo -e "${CYAN}📥 Downloading binary...${NC}"
if ! curl -fsSL -o "$TARBALL" "$DOWNLOAD_URL"; then
    echo -e "${RED}✗ Download failed!${NC}"
    echo -e "${YELLOW}  Check if release exists: https://github.com/hoshiyomiX/InjectTools/releases/tag/$VERSION${NC}"
    rm -rf "$TMP_DIR"
    exit 1
fi
echo -e "${GREEN}✓ Downloaded: $TARBALL${NC}"

# Download & verify checksum (optional, skip if not available)
echo -e "${CYAN}🔐 Verifying checksum...${NC}"
if curl -fsSL -o "$TARBALL.sha256" "$CHECKSUM_URL" 2>/dev/null; then
    if sha256sum -c "$TARBALL.sha256" --status 2>/dev/null; then
        echo -e "${GREEN}✓ Checksum verified${NC}"
    else
        echo -e "${YELLOW}⚠ Checksum verification failed (continuing anyway)${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Checksum not available (skipping verification)${NC}"
fi

# Extract
echo -e "${CYAN}📂 Extracting...${NC}"
if ! tar xzf "$TARBALL"; then
    echo -e "${RED}✗ Extraction failed!${NC}"
    rm -rf "$TMP_DIR"
    exit 1
fi
echo -e "${GREEN}✓ Extracted${NC}"

# Check if binary exists
if [[ ! -f "injecttools" ]]; then
    echo -e "${RED}✗ Binary not found in archive${NC}"
    rm -rf "$TMP_DIR"
    exit 1
fi

# Backup existing installation
if [[ -f "$PREFIX/bin/injecttools" ]]; then
    echo -e "${YELLOW}⚠ Existing installation found, creating backup...${NC}"
    mv "$PREFIX/bin/injecttools" "$PREFIX/bin/injecttools.backup.$(date +%s)"
    echo -e "${GREEN}✓ Backup created${NC}"
fi

# Install
echo -e "${CYAN}📦 Installing to $PREFIX/bin/...${NC}"
mv injecttools "$PREFIX/bin/"
chmod +x "$PREFIX/bin/injecttools"
echo -e "${GREEN}✓ Installed${NC}"

# Cleanup
cd ~
rm -rf "$TMP_DIR"
echo -e "${GREEN}✓ Cleaned up temp files${NC}"

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Installation complete!${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}📍 Binary location: ${GREEN}$PREFIX/bin/injecttools${NC}"
echo -e "${BLUE}📊 Binary size: ${GREEN}$(du -h $PREFIX/bin/injecttools | cut -f1)${NC}"
echo ""
echo -e "${YELLOW}🚀 Run the tool:${NC}"
echo -e "   ${CYAN}injecttools${NC}"
echo ""
echo -e "${YELLOW}📖 Help & Options:${NC}"
echo -e "   ${CYAN}injecttools --help${NC}"
echo ""
echo -e "${BLUE}Created by: ${CYAN}t.me/hoshiyomi_id${NC}"
echo ""

# Test binary
if injecttools --version &>/dev/null; then
    echo -e "${GREEN}✓ Installation verified successfully!${NC}"
else
    echo -e "${YELLOW}⚠ Binary installed but verification failed${NC}"
    echo -e "${YELLOW}  Try running: ${CYAN}injecttools --version${NC}"
fi