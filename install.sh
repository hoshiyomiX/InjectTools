#!/bin/bash
# InjectTools - Force Latest Release Installer
# Usage: curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/beta/install.sh | bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

clear
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}   InjectTools - Force Latest Installer${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check Termux
if [[ -z "$PREFIX" ]]; then
    echo -e "${RED}✗ Error: Not running in Termux${NC}"
    echo -e "${YELLOW}  This installer is for Termux on Android only${NC}"
    exit 1
fi

# Detect architecture
ARCH=$(uname -m)
echo -e "${BLUE}📱 Architecture: ${GREEN}$ARCH${NC}"

if [[ "$ARCH" == "aarch64" ]]; then
    ASSET_PATTERN="arm64"
    RUST_TARGET="aarch64-linux-android"
    echo -e "${GREEN}✓ Target: ARM64${NC}"
elif [[ "$ARCH" == "armv7"* ]] || [[ "$ARCH" == "armv8l" ]]; then
    ASSET_PATTERN="armv7"
    RUST_TARGET="armv7-linux-androideabi"
    echo -e "${GREEN}✓ Target: ARMv7${NC}"
else
    echo -e "${RED}✗ Unsupported architecture: $ARCH${NC}"
    exit 1
fi

echo ""

# Function: Force clean old installation
force_clean() {
    local bin_path="$PREFIX/bin/injecttools"
    
    if [[ -f "$bin_path" ]]; then
        echo -e "${YELLOW}🗑️  Removing old installation...${NC}"
        
        # Get old version if possible
        local old_ver=$("$bin_path" --version 2>/dev/null || echo "unknown")
        [[ "$old_ver" != "unknown" ]] && echo -e "${YELLOW}  Old: $old_ver${NC}"
        
        # Kill any running instances
        pkill -9 injecttools 2>/dev/null || true
        sleep 0.3
        
        # Force remove
        rm -f "$bin_path" 2>/dev/null || true
        
        if [[ -f "$bin_path" ]]; then
            echo -e "${RED}✗ Failed to remove old binary${NC}"
            echo -e "${YELLOW}  Run: chmod 777 $bin_path && rm -f $bin_path${NC}"
            return 1
        fi
        
        echo -e "${GREEN}✓ Cleaned${NC}"
    fi
}

# Function: Install binary
install_binary() {
    local source="$1"
    local target="$PREFIX/bin/injecttools"
    
    # Verify source is valid ELF
    if ! file "$source" | grep -q "ELF"; then
        echo -e "${RED}✗ Invalid binary format${NC}"
        return 1
    fi
    
    # Make executable
    chmod +x "$source"
    
    # Get version
    local version=$("$source" --version 2>/dev/null || echo "unknown")
    echo -e "${BLUE}  Version: ${GREEN}$version${NC}"
    
    # Force clean old
    force_clean || return 1
    
    # Install
    echo -e "${CYAN}  📥 Installing...${NC}"
    cp -f "$source" "$target" || {
        # Fallback: cat method
        cat "$source" > "$target" || {
            echo -e "${RED}✗ Install failed${NC}"
            return 1
        }
    }
    chmod 755 "$target"
    
    # Verify
    sleep 0.3
    if ! "$target" --version &>/dev/null; then
        echo -e "${RED}✗ Binary not executable after install${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ Installed: $("$target" --version)${NC}"
    return 0
}

# Function: Force download from latest release
force_download_latest() {
    echo -e "${CYAN}🔍 Fetching latest release info...${NC}"
    
    # Get latest release data
    local api_url="https://api.github.com/repos/hoshiyomiX/InjectTools/releases/latest"
    local release_data=$(curl -fsSL "$api_url" 2>/dev/null)
    
    if [[ -z "$release_data" ]]; then
        echo -e "${RED}✗ Failed to fetch release data${NC}"
        return 1
    fi
    
    # Extract tag name
    local tag=$(echo "$release_data" | grep -m1 '"tag_name"' | sed -E 's/.*"tag_name": "([^"]+)".*/\1/')
    echo -e "${GREEN}✓ Latest release: ${CYAN}$tag${NC}"
    
    # Find asset matching architecture
    local asset_url=$(echo "$release_data" | \
        grep "browser_download_url.*${ASSET_PATTERN}.tar.gz" | \
        head -1 | \
        sed -E 's/.*"browser_download_url": "([^"]+)".*/\1/')
    
    if [[ -z "$asset_url" ]]; then
        echo -e "${RED}✗ No asset found for $ASSET_PATTERN${NC}"
        echo -e "${YELLOW}  Assets available:${NC}"
        echo "$release_data" | grep "browser_download_url" | sed -E 's/.*"([^"]+)".*/  - \1/'
        return 1
    fi
    
    echo -e "${BLUE}  Asset URL: ${CYAN}${asset_url##*/}${NC}"
    
    # Download
    local tmp_dir=$(mktemp -d)
    cd "$tmp_dir"
    
    echo -e "${CYAN}⬇️  Downloading...${NC}"
    if ! curl -fsSL -o "release.tar.gz" "$asset_url"; then
        echo -e "${RED}✗ Download failed${NC}"
        cd ~ && rm -rf "$tmp_dir"
        return 1
    fi
    echo -e "${GREEN}✓ Downloaded ($(du -h release.tar.gz | cut -f1))${NC}"
    
    # Extract
    echo -e "${CYAN}📦 Extracting...${NC}"
    if ! tar xzf "release.tar.gz"; then
        echo -e "${RED}✗ Extraction failed${NC}"
        cd ~ && rm -rf "$tmp_dir"
        return 1
    fi
    
    if [[ ! -f "injecttools" ]]; then
        echo -e "${RED}✗ Binary not found in archive${NC}"
        cd ~ && rm -rf "$tmp_dir"
        return 1
    fi
    echo -e "${GREEN}✓ Extracted${NC}"
    
    # Install
    if install_binary "$PWD/injecttools"; then
        cd ~ && rm -rf "$tmp_dir"
        return 0
    else
        cd ~ && rm -rf "$tmp_dir"
        return 1
    fi
}

# Function: Build from source (fallback)
build_from_source() {
    echo -e "${CYAN}🔨 Building from source...${NC}"
    
    # Install dependencies
    local deps=(git rust binutils)
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &>/dev/null; then
            echo -e "${CYAN}  → Installing $dep${NC}"
            pkg install -y "$dep" >/dev/null 2>&1
        fi
    done
    
    # Clean old build
    local build_dir="$HOME/InjectTools-build"
    [[ -d "$build_dir" ]] && rm -rf "$build_dir"
    
    # Clone
    echo -e "${CYAN}📥 Cloning repo...${NC}"
    if ! git clone --depth 1 -q https://github.com/hoshiyomiX/InjectTools.git "$build_dir"; then
        echo -e "${RED}✗ Git clone failed${NC}"
        return 1
    fi
    
    cd "$build_dir"
    
    # Build
    echo -e "${CYAN}🔧 Compiling (5-15 min)...${NC}"
    echo -e "${YELLOW}  Grab some coffee ☕${NC}"
    
    if cargo build --release --target "$RUST_TARGET" 2>&1 | \
       grep --line-buffered -E '(Compiling|Finished|error:)'; then
        
        local binary="target/$RUST_TARGET/release/injecttools"
        
        if [[ -f "$binary" ]]; then
            echo -e "${GREEN}✓ Build complete${NC}"
            
            if install_binary "$binary"; then
                cd ~ && rm -rf "$build_dir"
                return 0
            fi
        fi
    fi
    
    echo -e "${RED}✗ Build failed${NC}"
    cd ~ && rm -rf "$build_dir"
    return 1
}

# Main execution
INSTALLED=false

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}FORCE MODE: Installing latest release${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Try download from latest release
if force_download_latest; then
    INSTALLED=true
fi

# Fallback: Build from source
if [[ "$INSTALLED" == "false" ]]; then
    echo ""
    echo -e "${YELLOW}⚠️  No pre-built release available${NC}"
    echo -e "${YELLOW}⚠️  Building from source as fallback${NC}"
    echo ""
    
    if build_from_source; then
        INSTALLED=true
    else
        echo -e "${RED}✗ Installation failed completely${NC}"
        exit 1
    fi
fi

# Success report
echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Installation Complete!${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [[ -f "$PREFIX/bin/injecttools" ]]; then
    echo -e "${BLUE}📍 Binary: ${GREEN}$PREFIX/bin/injecttools${NC}"
    echo -e "${BLUE}📊 Size: ${GREEN}$(du -h $PREFIX/bin/injecttools | cut -f1)${NC}"
    echo -e "${BLUE}🔖 Version: ${GREEN}$(injecttools --version 2>&1)${NC}"
    
    # Quick test
    echo ""
    echo -e "${YELLOW}🧪 Testing...${NC}"
    if injecttools --version &>/dev/null; then
        echo -e "${GREEN}✓ Binary is functional${NC}"
    else
        echo -e "${RED}✗ Binary test failed${NC}"
    fi
fi

echo ""
echo -e "${YELLOW}🚀 Usage:${NC}"
echo -e "   ${CYAN}injecttools${NC}                    ${BLUE}# Interactive menu${NC}"
echo -e "   ${CYAN}injecttools --help${NC}             ${BLUE}# Show options${NC}"
echo -e "   ${CYAN}injecttools -t host -s subdomain${NC}"
echo ""
echo -e "${BLUE}👤 @hoshiyomi_id${NC}"
echo -e "${BLUE}🐛 github.com/hoshiyomiX/InjectTools/issues${NC}"
echo ""
