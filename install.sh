#!/bin/bash
# InjectTools - Smart Installer for Termux
# Usage: curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
# Or with version: curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash -s termux-v2.3.1

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}   InjectTools - Smart Installer${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if running in Termux
if [[ -z "$PREFIX" ]]; then
    echo -e "${RED}✗ Error: Not running in Termux${NC}"
    echo -e "${YELLOW}  This installer is designed for Termux on Android${NC}"
    exit 1
fi

# Detect architecture
ARCH=$(uname -m)
echo -e "${BLUE}📱 Architecture: ${GREEN}$ARCH${NC}"

if [[ "$ARCH" == "aarch64" ]]; then
    BINARY_NAME="injecttools-termux-arm64"
    RUST_TARGET="aarch64-linux-android"
    echo -e "${GREEN}✓ Target: ARM64 (modern devices)${NC}"
elif [[ "$ARCH" == "armv7"* ]] || [[ "$ARCH" == "armv8l" ]]; then
    BINARY_NAME="injecttools-termux-armv7"
    RUST_TARGET="armv7-linux-androideabi"
    echo -e "${GREEN}✓ Target: ARMv7 (older devices)${NC}"
else
    echo -e "${RED}✗ Unsupported architecture: $ARCH${NC}"
    echo -e "${YELLOW}  Supported: aarch64, armv7, armv8l${NC}"
    exit 1
fi

echo ""

# Function to force uninstall old binary
force_uninstall() {
    local target_path="$PREFIX/bin/injecttools"
    
    if [[ -f "$target_path" ]]; then
        echo -e "${YELLOW}🗑️  Removing old installation...${NC}"
        
        # Get old version for reference
        local old_version=$("$target_path" --version 2>/dev/null || echo "unknown")
        echo -e "${YELLOW}  Old: $old_version${NC}"
        
        # Kill any running instances
        pkill -9 injecttools 2>/dev/null || true
        sleep 0.5
        
        # Multiple removal attempts
        rm -f "$target_path" 2>/dev/null || true
        rm -rf "$target_path" 2>/dev/null || true
        
        # Verify removal
        if [[ -f "$target_path" ]]; then
            echo -e "${RED}✗ Failed to remove old binary (permission issue?)${NC}"
            echo -e "${YELLOW}  Try: chmod 777 $target_path && rm -f $target_path${NC}"
            return 1
        fi
        
        echo -e "${GREEN}✓ Old binary removed${NC}"
    fi
    
    return 0
}

# Function to safely install binary with verification
safe_install_binary() {
    local source_binary="$1"
    local target_path="$PREFIX/bin/injecttools"
    
    # Verify source exists and is executable
    if [[ ! -f "$source_binary" ]]; then
        echo -e "${RED}✗ Source binary not found: $source_binary${NC}"
        return 1
    fi
    
    # Check if it's a valid ELF binary
    if ! file "$source_binary" | grep -q "ELF"; then
        echo -e "${RED}✗ Invalid binary format${NC}"
        file "$source_binary"
        return 1
    fi
    
    # Get version from new binary
    chmod +x "$source_binary"
    local new_version=$("$source_binary" --version 2>/dev/null || echo "unknown")
    echo -e "${BLUE}  New version: ${GREEN}$new_version${NC}"
    
    # Extract version number for comparison
    local new_ver_num=$(echo "$new_version" | grep -oP '\d+\.\d+\.\d+' | head -1)
    echo -e "${BLUE}  Version number: ${CYAN}$new_ver_num${NC}"
    
    # Force remove old binary
    if ! force_uninstall; then
        return 1
    fi
    
    # Install new binary
    echo -e "${CYAN}  📥 Installing new binary...${NC}"
    
    # Use cp with force overwrite
    cp -f "$source_binary" "$target_path" 2>/dev/null || {
        echo -e "${RED}✗ Copy failed, trying with sudo-like approach${NC}"
        cat "$source_binary" > "$target_path" || {
            echo -e "${RED}✗ Installation failed${NC}"
            return 1
        }
    }
    
    chmod 755 "$target_path"
    
    # Verify installation
    if [[ ! -f "$target_path" ]]; then
        echo -e "${RED}✗ Installation failed: binary not found after copy${NC}"
        return 1
    fi
    
    # Verify executable
    sleep 0.5
    if ! "$target_path" --version &>/dev/null; then
        echo -e "${RED}✗ Installation failed: binary not executable${NC}"
        ls -la "$target_path"
        return 1
    fi
    
    # Get installed version and verify
    local installed_version=$("$target_path" --version 2>&1)
    echo -e "${GREEN}✓ Installed: $installed_version${NC}"
    
    # Cross-check version
    local inst_ver_num=$(echo "$installed_version" | grep -oP '\d+\.\d+\.\d+' | head -1)
    if [[ "$new_ver_num" != "$inst_ver_num" ]]; then
        echo -e "${YELLOW}⚠ Version mismatch detected!${NC}"
        echo -e "${YELLOW}  Expected: $new_ver_num${NC}"
        echo -e "${YELLOW}  Got: $inst_ver_num${NC}"
        echo -e "${RED}✗ Installation verification failed${NC}"
        return 1
    fi
    
    return 0
}

# Function to try download from GitHub releases
try_download_release() {
    local version="$1"
    local base_url="https://github.com/hoshiyomiX/InjectTools/releases/download/$version"
    local tarball="${BINARY_NAME}.tar.gz"
    local download_url="$base_url/$tarball"
    
    echo -e "${BLUE}  Trying: ${CYAN}$version${NC}"
    echo -e "${BLUE}  URL: ${CYAN}$download_url${NC}"
    
    local tmp_dir=$(mktemp -d)
    cd "$tmp_dir"
    
    # Download with verbose error
    if curl -fsSL -o "$tarball" "$download_url" 2>&1; then
        echo -e "${GREEN}  ✓ Downloaded${NC}"
        
        # Extract
        if tar xzf "$tarball" 2>&1 && [[ -f "injecttools" ]]; then
            echo -e "${GREEN}  ✓ Extracted${NC}"
            
            # Show binary info
            echo -e "${BLUE}  Binary info:${NC}"
            ls -lh injecttools
            file injecttools
            
            # Use safe install
            if safe_install_binary "$PWD/injecttools"; then
                cd ~
                rm -rf "$tmp_dir"
                echo -e "${GREEN}✓ Installed from release: $version${NC}"
                return 0
            else
                echo -e "${RED}✗ Safe install failed${NC}"
            fi
        else
            echo -e "${RED}✗ Extraction failed${NC}"
        fi
    else
        echo -e "${RED}✗ Download failed (release might not exist)${NC}"
    fi
    
    cd ~
    rm -rf "$tmp_dir"
    return 1
}

# Function to fetch latest GitHub release tag
get_latest_release() {
    local api_url="https://api.github.com/repos/hoshiyomiX/InjectTools/releases"
    
    # Try to get termux-specific release first
    local termux_tag=$(curl -fsSL "$api_url" 2>/dev/null | \
        grep -m1 '"tag_name".*termux-' | \
        sed -E 's/.*"tag_name": "([^"]+)".*/\1/')
    
    if [[ -n "$termux_tag" ]]; then
        echo "$termux_tag"
        return 0
    fi
    
    # Fallback to any latest release
    local any_tag=$(curl -fsSL "$api_url" 2>/dev/null | \
        grep -m1 '"tag_name"' | \
        sed -E 's/.*"tag_name": "([^"]+)".*/\1/')
    
    if [[ -n "$any_tag" ]]; then
        echo "$any_tag"
        return 0
    fi
    
    return 1
}

# Function to build from source
build_from_source() {
    echo -e "${CYAN}🔨 Building from source...${NC}"
    echo ""
    
    # Check dependencies
    local missing=()
    
    command -v git &>/dev/null || missing+=("git")
    command -v rustc &>/dev/null || missing+=("rust")
    command -v pkg-config &>/dev/null || missing+=("binutils")
    
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo -e "${YELLOW}📦 Installing dependencies: ${missing[*]}${NC}"
        for pkg in "${missing[@]}"; do
            echo -e "${CYAN}  → $pkg${NC}"
            pkg install -y "$pkg" >/dev/null 2>&1
        done
        echo -e "${GREEN}✓ Dependencies installed${NC}"
        echo ""
    fi
    
    # Clone or update repo
    local build_dir="$HOME/InjectTools-build"
    
    if [[ -d "$build_dir" ]]; then
        echo -e "${YELLOW}⚠ Cleaning old build directory${NC}"
        rm -rf "$build_dir"
    fi
    
    echo -e "${CYAN}📥 Cloning repository...${NC}"
    if ! git clone --depth 1 -q https://github.com/hoshiyomiX/InjectTools.git "$build_dir"; then
        echo -e "${RED}✗ Git clone failed${NC}"
        return 1
    fi
    
    cd "$build_dir"
    
    # Show version being built
    echo -e "${BLUE}  Cargo.toml version:${NC}"
    grep '^version' Cargo.toml
    
    echo -e "${CYAN}🔧 Compiling (this may take 5-15 minutes)...${NC}"
    echo -e "${YELLOW}  Device will be busy, grab some coffee ☕${NC}"
    echo ""
    
    # Clean build
    cargo clean
    
    # Build with progress output
    if cargo build --release --target "$RUST_TARGET" 2>&1 | \
       grep --line-buffered -E '(Compiling|Finished|error:)' | \
       while IFS= read -r line; do
           if echo "$line" | grep -q "Compiling"; then
               echo -e "${BLUE}  $line${NC}"
           elif echo "$line" | grep -q "Finished"; then
               echo -e "${GREEN}  $line${NC}"
           elif echo "$line" | grep -q "error:"; then
               echo -e "${RED}  $line${NC}"
           fi
       done; then
        
        local binary_path="target/$RUST_TARGET/release/injecttools"
        
        if [[ -f "$binary_path" ]]; then
            echo ""
            echo -e "${GREEN}✓ Build complete${NC}"
            
            # Show build info
            ls -lh "$binary_path"
            "$binary_path" --version
            
            # Use safe install
            if safe_install_binary "$binary_path"; then
                cd ~
                rm -rf "$build_dir"
                echo -e "${GREEN}✓ Built and installed from source${NC}"
                return 0
            fi
        else
            echo -e "${RED}✗ Build succeeded but binary not found${NC}"
            cd ~
            return 1
        fi
    else
        echo -e "${RED}✗ Build failed${NC}"
        echo -e "${YELLOW}  Check build logs above for errors${NC}"
        cd ~
        return 1
    fi
}

# Main installation flow
INSTALLED=false

# Check if version specified by user
if [[ -n "$1" ]]; then
    echo -e "${BLUE}🎯 User-specified version: ${GREEN}$1${NC}"
    echo ""
    
    if try_download_release "$1"; then
        INSTALLED=true
    else
        echo -e "${RED}✗ Version $1 not found${NC}"
        echo -e "${YELLOW}  Available releases: https://github.com/hoshiyomiX/InjectTools/releases${NC}"
        echo ""
        echo -e "${CYAN}💡 Fallback to latest or build from source? (y/n)${NC}"
        read -r fallback
        if [[ ! "$fallback" =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}Installation cancelled${NC}"
            exit 1
        fi
    fi
fi

# Try to get latest release
if [[ "$INSTALLED" == "false" ]]; then
    echo -e "${CYAN}🔍 Checking for latest release...${NC}"
    
    if LATEST=$(get_latest_release); then
        echo -e "${GREEN}✓ Found: $LATEST${NC}"
        echo ""
        
        if try_download_release "$LATEST"; then
            INSTALLED=true
        fi
    fi
fi

# Try fallback versions
if [[ "$INSTALLED" == "false" ]]; then
    echo -e "${YELLOW}⚠ No latest release found, trying fallback versions...${NC}"
    echo ""
    
    FALLBACK_VERSIONS=("termux-v2.3.1" "termux-v2.3.0" "v2.3.1" "v2.3.0" "termux-v1.1.0" "v1.1.0")
    
    for version in "${FALLBACK_VERSIONS[@]}"; do
        if try_download_release "$version"; then
            INSTALLED=true
            break
        fi
    done
fi

# Last resort: build from source
if [[ "$INSTALLED" == "false" ]]; then
    echo -e "${YELLOW}⚠ No pre-built releases available${NC}"
    echo ""
    
    if build_from_source; then
        INSTALLED=true
    else
        echo -e "${RED}✗ Installation failed${NC}"
        exit 1
    fi
fi

# Final report
echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Installation Complete!${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [[ -f "$PREFIX/bin/injecttools" ]]; then
    echo -e "${BLUE}📍 Location: ${GREEN}$PREFIX/bin/injecttools${NC}"
    echo -e "${BLUE}📊 Size: ${GREEN}$(du -h $PREFIX/bin/injecttools | cut -f1)${NC}"
    
    if VERSION_OUT=$(injecttools --version 2>&1); then
        echo -e "${BLUE}🔖 Version: ${GREEN}$VERSION_OUT${NC}"
    fi
    
    # Show SHA256 for verification
    echo -e "${BLUE}🔐 SHA256: ${GREEN}$(sha256sum $PREFIX/bin/injecttools | cut -d' ' -f1 | head -c16)...${NC}"
    
    # Full path test
    echo ""
    echo -e "${YELLOW}🧪 Quick Test:${NC}"
    if $PREFIX/bin/injecttools --version &>/dev/null; then
        echo -e "${GREEN}✓ Binary is functional${NC}"
    else
        echo -e "${RED}✗ Binary test failed${NC}"
    fi
fi

echo ""
echo -e "${YELLOW}🚀 Quick Start:${NC}"
echo -e "   ${CYAN}injecttools${NC}              ${MAGENTA}# Interactive menu${NC}"
echo -e "   ${CYAN}injecttools --help${NC}       ${MAGENTA}# Show all options${NC}"
echo ""
echo -e "${YELLOW}📚 Examples:${NC}"
echo -e "   ${CYAN}injecttools -t host.com -s cdn.cloudflare.com${NC}"
echo -e "   ${CYAN}injecttools -t host.com -d cloudflare.com --crtsh${NC}"
echo -e "   ${CYAN}injecttools --view-results${NC}"
echo ""
echo -e "${BLUE}👤 Created by: ${CYAN}t.me/hoshiyomi_id${NC}"
echo -e "${BLUE}🐛 Report bugs: ${CYAN}github.com/hoshiyomiX/InjectTools/issues${NC}"
echo ""