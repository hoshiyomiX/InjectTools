#!/bin/bash

# InjectTools v1.1

# Bug Inject Scanner for Cloudflare Subdomains

# Created by: t.me/hoshiyomi_id

# Colors

RED='\033[0;31m'

GREEN='\033[0;32m'

YELLOW='\033[1;33m'

BLUE='\033[0;34m'

CYAN='\033[0;36m'

MAGENTA='\033[0;35m'

WHITE='\033[1;37m'

BOLD='\033[1m'

NC='\033[0m'

# Config file

CONFIG_FILE="/sdcard/injecttools-config.txt"

WORDLIST_DIR="/sdcard/bug-wordlists"

ACTIVE_WORDLIST="embedded"

SCAN_CANCELLED=false

# Default values

TARGET_HOST=""

DEFAULT_SUBDOMAIN=""

DEFAULT_DOMAIN=""

TIMEOUT=10

mkdir -p "$WORDLIST_DIR"

# Check and install dependencies

check_dependencies() {

local MISSING=()

# Check curl (WAJIB untuk HTTP testing)

if ! command -v curl &> /dev/null; then

MISSING+=("curl")

fi

# Check dig (optional, bisa pakai nslookup fallback)

if ! command -v dig &> /dev/null; then

if ! command -v nslookup &> /dev/null; then

MISSING+=("dnsutils")

fi

fi

if [ ${#MISSING[@]} -gt 0 ]; then

clear

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "${WHITE}${BOLD} Dependency Check${NC}"

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""

echo -e "${RED}${BOLD}Beberapa package yang dibutuhkan belum terinstall:${NC}"

echo ""

for pkg in "${MISSING[@]}"; do

echo -e "  ${RED}✗${NC} ${WHITE}$pkg${NC}"

done

echo ""

echo -e "${BLUE}Package ini dibutuhkan untuk menjalankan InjectTools.${NC}"

echo ""

echo -e "${WHITE}Install otomatis sekarang? (y/n)${NC}"

read -p "> " INSTALL_CHOICE

if [[ "$INSTALL_CHOICE" =~ ^[Yy]$ ]]; then

echo ""

echo -e "${CYAN}📦 Installing dependencies...${NC}"

echo ""

for pkg in "${MISSING[@]}"; do

echo -e "${YELLOW}Installing $pkg...${NC}"

if pkg install -y $pkg; then

echo -e "${GREEN}✓ $pkg installed${NC}"

else

echo -e "${RED}✗ Failed to install $pkg${NC}"

echo ""

echo -e "${YELLOW}Silakan install manual dengan:${NC}"

echo -e "${WHITE}pkg install $pkg${NC}"

echo ""

read -p "Tekan Enter untuk exit..."

exit 1

fi

done

echo ""

echo -e "${GREEN}${BOLD}✅ Semua dependencies terinstall!${NC}"

echo ""

sleep 2

else

echo ""

echo -e "${YELLOW}Script membutuhkan dependencies untuk berjalan.${NC}"

echo -e "${WHITE}Install manual dengan:${NC}"

echo ""

for pkg in "${MISSING[@]}"; do

echo -e "  ${CYAN}pkg install $pkg${NC}"

done

echo ""

read -p "Tekan Enter untuk exit..."

exit 1

fi

fi

}



# Get terminal width and center text

center_text() {

local text="$1"

local width=$(tput cols 2>/dev/null || echo 60)

local len=${#text}

local padding=$(( (width - len) / 2 ))

[ $padding -gt 0 ] && printf "%${padding}s" ""

echo "$text"

}



# Print centered header

print_header() {

local title="$1"

local width=$(tput cols 2>/dev/null || echo 60)

printf "${CYAN}"

printf '═%.0s' $(seq 1 $width)

printf "${NC}\n"

echo -e "${WHITE}$(center_text "$title")${NC}"

printf "${CYAN}"

printf '═%.0s' $(seq 1 $width)

printf "${NC}\n"

}



# Load config

load_config() {

if [ -f "$CONFIG_FILE" ]; then

source "$CONFIG_FILE"

return 0

fi

return 1

}



# Save config

save_config() {

cat > "$CONFIG_FILE" << EOF

# InjectTools Configuration

TARGET_HOST="$TARGET_HOST"

DEFAULT_SUBDOMAIN="$DEFAULT_SUBDOMAIN"

DEFAULT_DOMAIN="$DEFAULT_DOMAIN"

TIMEOUT=$TIMEOUT

ACTIVE_WORDLIST="$ACTIVE_WORDLIST"

EOF

echo -e "${GREEN}✅ Konfigurasi disimpan ke: $CONFIG_FILE${NC}"

}



# Extract domain from subdomain

extract_domain() {

local SUBDOMAIN=$1

SUBDOMAIN=${SUBDOMAIN#*://}

echo "$SUBDOMAIN" | awk -F. '{if (NF>=2) print $(NF-1)"."$NF; else print $0}'

}



# Auto-detect and set best wordlist

auto_detect_wordlist() {

if [ -d "$WORDLIST_DIR" ] && [ "$(ls -A $WORDLIST_DIR/*.txt 2>/dev/null)" ]; then

LARGEST=""

MAX_LINES=0

for file in "$WORDLIST_DIR"/*.txt; do

if [ -f "$file" ]; then

LINES=$(wc -l < "$file" 2>/dev/null || echo "0")

if [ $LINES -gt $MAX_LINES ]; then

MAX_LINES=$LINES

LARGEST="$file"

fi

fi

done

if [ -n "$LARGEST" ]; then

ACTIVE_WORDLIST="$LARGEST"

return 0

fi

fi

return 1

}



# First time setup

first_time_setup() {

clear

print_header "SETUP AWAL"

echo ""

echo -e "${WHITE}Selamat datang di InjectTools!${NC}"

echo -e "${CYAN}Created by: t.me/hoshiyomi_id${NC}"

echo ""

echo -e "${BLUE}Mari kita atur konfigurasi default kamu.${NC}"

echo ""

# Setup Target Host

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "${WHITE}${BOLD}1. Setup Target Host${NC}"

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""

echo -e "${BLUE}Ini adalah domain tunnel/proxy kamu tempat bug inject akan connect.${NC}"

echo -e "${YELLOW}Contoh: your-tunnel.com, proxy.example.com${NC}"

echo ""

echo -e "${WHITE}Masukkan target host:${NC}"

read -p "> " TARGET_HOST

while [ -z "$TARGET_HOST" ]; do

echo -e "${RED}Target host tidak boleh kosong!${NC}"

read -p "> " TARGET_HOST

done

echo -e "${GREEN}✓ Target host diset: $TARGET_HOST${NC}"

echo ""

sleep 1

# Setup Default Subdomain

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "${WHITE}${BOLD}2. Setup Default Subdomain${NC}"

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""

echo -e "${BLUE}Subdomain default untuk quick test (Menu 1).${NC}"

echo -e "${YELLOW}Contoh: cdn.example.com, api.target.com${NC}"

echo ""

echo -e "${WHITE}Masukkan default subdomain:${NC}"

read -p "> " DEFAULT_SUBDOMAIN

while [ -z "$DEFAULT_SUBDOMAIN" ]; do

echo -e "${RED}Default subdomain tidak boleh kosong!${NC}"

read -p "> " DEFAULT_SUBDOMAIN

done

echo -e "${GREEN}✓ Default subdomain diset: $DEFAULT_SUBDOMAIN${NC}"

echo ""

# Auto-detect domain

DEFAULT_DOMAIN=$(extract_domain "$DEFAULT_SUBDOMAIN")

echo -e "${BLUE}📌 Domain auto-detected: ${GREEN}$DEFAULT_DOMAIN${NC}"

echo -e "${YELLOW}   (extracted from subdomain)${NC}"

echo ""

sleep 2

# Wordlist Setup

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "${WHITE}${BOLD}3. Setup Wordlist${NC}"

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""

if auto_detect_wordlist; then

WNAME=$(basename "$ACTIVE_WORDLIST")

WLINES=$(wc -l < "$ACTIVE_WORDLIST" 2>/dev/null || echo "0")

echo -e "${GREEN}${BOLD}✅ Wordlist terdeteksi!${NC}"

echo -e "${WHITE}   File:${NC} ${GREEN}$WNAME${NC}"

echo -e "${WHITE}   Lines:${NC} ${CYAN}$WLINES patterns${NC}"

echo ""

echo -e "${BLUE}Wordlist ini akan digunakan sebagai default.${NC}"

echo ""

echo -e "${YELLOW}Download wordlist tambahan? (y/n):${NC}"

read -p "> " DOWNLOAD_CHOICE

if [[ "$DOWNLOAD_CHOICE" =~ ^[Yy]$ ]]; then

download_wordlist_menu

fi

else

echo -e "${YELLOW}Tidak ada wordlist terdeteksi.${NC}"

echo ""

echo -e "${BLUE}Untuk hasil scan lebih baik, download wordlist komprehensif${NC}"

echo -e "${BLUE}dari SecLists (5K sampai 110K subdomain).${NC}"

echo ""

echo -e "${YELLOW}Saat ini: Embedded wordlist (coverage terbatas)${NC}"

echo ""

echo -e "${WHITE}Download wordlist sekarang? (y/n)${NC}"

read -p "> " DOWNLOAD_CHOICE

if [[ "$DOWNLOAD_CHOICE" =~ ^[Yy]$ ]]; then

download_wordlist_menu

auto_detect_wordlist

fi

fi

# Save configuration

echo ""

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "${WHITE}${BOLD}Setup Selesai!${NC}"

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""

echo -e "${BLUE}Konfigurasi kamu:${NC}"

echo -e "  ${WHITE}Target Host:${NC} ${GREEN}$TARGET_HOST${NC}"

echo -e "  ${WHITE}Default Subdomain:${NC} ${GREEN}$DEFAULT_SUBDOMAIN${NC}"

echo -e "  ${WHITE}Default Domain:${NC} ${GREEN}$DEFAULT_DOMAIN${NC}"

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

echo -e "  ${WHITE}Wordlist:${NC} ${YELLOW}Embedded (terbatas)${NC}"

else

WNAME=$(basename "$ACTIVE_WORDLIST")

WLINES=$(wc -l < "$ACTIVE_WORDLIST" 2>/dev/null || echo "0")

echo -e "  ${WHITE}Wordlist:${NC} ${GREEN}$WNAME${NC} ${CYAN}($WLINES lines)${NC}"

fi

echo ""

save_config

echo ""

echo -e "${BLUE}Kamu bisa ubah setting ini kapan saja dari Menu 4 (Settings).${NC}"

echo ""

read -p "Tekan Enter untuk lanjut..."

}



# Check if Cloudflare

is_cloudflare() {

local IP=$1

[[ "$IP" =~ ^104\.(1[6-9]|2[0-9]|3[0-1])\. ]] || \

[[ "$IP" =~ ^172\.(6[4-9]|7[0-1])\. ]] || \

[[ "$IP" =~ ^173\.245\. ]] || \

[[ "$IP" =~ ^162\.15[89]\. ]] || \

[[ "$IP" =~ ^141\.101\. ]]

}



# Resolve domain to IP (with fallback)

resolve_to_ip() {

local DOMAIN=$1

local IP=""

# Try dig first (preferred)

if command -v dig &> /dev/null; then

IP=$(dig +short $DOMAIN 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -n1)

fi

# Fallback to nslookup (built-in Android)

if [ -z "$IP" ] && command -v nslookup &> /dev/null; then

IP=$(nslookup $DOMAIN 2>/dev/null | awk '/^Address: / { print $2 }' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -n1)

fi

echo "$IP"

}



# Prompt to download wordlist before scan

prompt_download_wordlist() {

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

clear

print_header "⚠️  Wordlist Terbatas Terdeteksi"

echo ""

echo -e "${YELLOW}${BOLD}Kamu sedang menggunakan embedded wordlist (coverage terbatas).${NC}"

echo ""

echo -e "${BLUE}Untuk hasil lebih baik, download wordlist komprehensif:${NC}"

echo -e "  • ${GREEN}Small${NC} - 5,000 pattern (scan cepat)"

echo -e "  • ${GREEN}Medium${NC} - 20,000 pattern (seimbang)"

echo -e "  • ${GREEN}Large${NC} - 110,000 pattern (coverage maksimal)"

echo ""

echo -e "${WHITE}${BOLD}Download wordlist sekarang?${NC}"

echo ""

echo -e "  ${WHITE}1.${NC} Ya, download sekarang"

echo -e "  ${WHITE}2.${NC} Tidak, lanjut dengan embedded (tidak disarankan)"

echo -e "  ${WHITE}3.${NC} Batal scan"

echo ""

read -p "Pilih [1-3]: " CHOICE

case $CHOICE in

1)

download_wordlist_menu

auto_detect_wordlist

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

echo ""

echo -e "${YELLOW}Masih pakai embedded. Lanjut tetap? (y/n)${NC}"

read -p "> " CONTINUE

[[ ! "$CONTINUE" =~ ^[Yy]$ ]] && return 1

fi

return 0

;;

2)

echo ""

echo -e "${YELLOW}Melanjutkan dengan embedded wordlist...${NC}"

sleep 1

return 0

;;

3)

return 1

;;

*)

echo -e "${RED}Pilihan tidak valid${NC}"

sleep 1

return 1

;;

esac

fi

return 0

}



# Test single subdomain

test_single_subdomain() {

clear

print_header "Test Single Subdomain"

echo ""

echo -e "${WHITE}Masukkan subdomain [default: ${CYAN}$DEFAULT_SUBDOMAIN${WHITE}]:${NC}"

read -p "> " SUBDOMAIN

SUBDOMAIN=${SUBDOMAIN:-$DEFAULT_SUBDOMAIN}

echo ""

echo -e "${BLUE}Target Host:${NC} ${GREEN}$TARGET_HOST${NC}"

echo -e "${BLUE}Testing:${NC} ${YELLOW}$SUBDOMAIN${NC}"

echo ""

echo -e "${CYAN}🔍 Resolving DNS...${NC}"

IP=$(resolve_to_ip "$SUBDOMAIN")

if [ -z "$IP" ]; then

echo -e "${RED}${BOLD}❌ IP tidak ditemukan untuk $SUBDOMAIN${NC}"

echo ""

read -p "Tekan Enter untuk lanjut..."

return

fi

echo -e "${WHITE}   IP Address:${NC} ${BLUE}$IP${NC}"

if is_cloudflare "$IP"; then

echo -e "${WHITE}   Provider:${NC} ${CYAN}☁️  Cloudflare${NC}"

else

echo -e "${WHITE}   Provider:${NC} ${YELLOW}⚠️  Non-Cloudflare${NC}"

fi

echo ""

echo -e "${CYAN}🧪 Testing bug inject...${NC}"

if curl -s --max-time $TIMEOUT \

--resolve $TARGET_HOST:443:$IP \

https://$TARGET_HOST/ -o /dev/null 2>&1; then

echo ""

echo -e "${GREEN}╔═══════════════════════════════════════════════════════╗${NC}"

echo -e "${GREEN}║           ✅ BUG INJECT WORKING!                      ║${NC}"

echo -e "${GREEN}╚═══════════════════════════════════════════════════════╝${NC}"

echo ""

echo -e "${WHITE}   Subdomain:${NC} ${GREEN}$SUBDOMAIN${NC}"

echo -e "${WHITE}   IP:${NC} ${GREEN}$IP${NC}"

echo -e "${WHITE}   Target:${NC} ${GREEN}$TARGET_HOST${NC}"

else

echo ""

echo -e "${RED}╔═══════════════════════════════════════════════════════╗${NC}"

echo -e "${RED}║            ❌ BUG INJECT FAILED                       ║${NC}"

echo -e "${RED}╚═══════════════════════════════════════════════════════╝${NC}"

echo ""

echo -e "${WHITE}   Subdomain:${NC} ${RED}$SUBDOMAIN${NC}"

echo -e "${WHITE}   IP:${NC} ${RED}$IP${NC}"

echo -e "${WHITE}   Alasan:${NC} ${YELLOW}Koneksi gagal atau diblokir${NC}"

fi

echo ""

read -p "Tekan Enter untuk lanjut..."

}



# Embedded wordlist

generate_embedded_wordlist() {

cat << 'EOF'

www

www1

www2

www3

m

mobile

app

api

api1

api2

cdn

cdn1

cdn2

cdn3

static

assets

content

cf

cf-cdn

cf-vod

cf-live

cf-stream

vod

vod1

video

stream

live

media

img

image

upload

download

mail

email

ads

ad

campus

learn

student

portal

dashboard

admin

auth

login

shop

blog

news

support

help

dev

staging

test

beta

db

web

server

vpn

dns

android

ios

origin

backup

search

s3

ir

investor

corporate

finance

a

b

c

d

e

f

g

h

i

j

k

l

n

o

p

q

r

s

t

u

v

w

x

y

z

EOF

}



# Download wordlist menu

download_wordlist_menu() {

clear

print_header "Download Wordlist (from SecLists)"

echo ""

echo -e "${WHITE}${BOLD}Wordlist Tersedia:${NC}"

echo ""

echo -e "  ${WHITE}1.${NC} Small - 5,000 subdomains ${CYAN}(~90 KB)${NC}"

echo -e "  ${WHITE}2.${NC} Medium - 20,000 subdomains ${CYAN}(~350 KB)${NC}"

echo -e "  ${WHITE}3.${NC} Large - 110,000 subdomains ${CYAN}(~2 MB)${NC}"

echo -e "  ${WHITE}4.${NC} Custom URL"

echo -e "  ${WHITE}5.${NC} View Downloaded Wordlists"

echo -e "  ${WHITE}6.${NC} Delete Wordlists"

echo -e "  ${WHITE}7.${NC} Back"

echo ""

read -p "Pilih [1-7]: " choice

case $choice in

1)

download_wordlist "small" \

"https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-5000.txt" \

"seclists-5k.txt"

;;

2)

download_wordlist "medium" \

"https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-20000.txt" \

"seclists-20k.txt"

;;

3)

download_wordlist "large" \

"https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-110000.txt" \

"seclists-110k.txt"

;;

4)

echo ""

echo -e "${WHITE}Masukkan custom URL:${NC}"

read -p "> " CUSTOM_URL

echo -e "${WHITE}Nama file:${NC}"

read -p "> " CUSTOM_NAME

if [ -n "$CUSTOM_URL" ] && [ -n "$CUSTOM_NAME" ]; then

download_wordlist "custom" "$CUSTOM_URL" "$CUSTOM_NAME"

else

echo -e "${RED}❌ Input tidak valid${NC}"

sleep 2

fi

;;

5) view_wordlists ;;

6) delete_wordlists ;;

7) return ;;

*) echo -e "${RED}Pilihan tidak valid${NC}"; sleep 1 ;;

esac

download_wordlist_menu

}



# Download function

download_wordlist() {

local SIZE=$1

local URL=$2

local FILENAME=$3

local FILEPATH="$WORDLIST_DIR/$FILENAME"

echo ""

echo -e "${CYAN}📥 Downloading $SIZE wordlist...${NC}"

echo ""

if [ -f "$FILEPATH" ]; then

echo -e "${YELLOW}⚠️  File sudah ada! Timpa? (y/n):${NC}"

read -p "> " OVERWRITE

[[ ! "$OVERWRITE" =~ ^[Yy]$ ]] && return

fi

if curl -L --progress-bar -o "$FILEPATH" "$URL" 2>&1; then

if [ -f "$FILEPATH" ] && [ -s "$FILEPATH" ]; then

LINE_COUNT=$(wc -l < "$FILEPATH")

FILE_SIZE=$(du -h "$FILEPATH" | cut -f1)

echo ""

echo -e "${GREEN}${BOLD}✅ Berhasil! Baris: $LINE_COUNT | Ukuran: $FILE_SIZE${NC}"

echo ""

echo -e "${WHITE}Set sebagai aktif? (y/n):${NC}"

read -p "> " SET_ACTIVE

if [[ "$SET_ACTIVE" =~ ^[Yy]$ ]]; then

ACTIVE_WORDLIST="$FILEPATH"

save_config

echo -e "${GREEN}✅ Aktif: $FILENAME${NC}"

fi

else

echo -e "${RED}${BOLD}❌ Download gagal${NC}"

rm -f "$FILEPATH"

fi

else

echo -e "${RED}${BOLD}❌ Download gagal${NC}"

fi

echo ""

read -p "Tekan Enter..."

}



# View wordlists

view_wordlists() {

clear

print_header "Downloaded Wordlists"

echo ""

if [ -d "$WORDLIST_DIR" ] && [ "$(ls -A $WORDLIST_DIR/*.txt 2>/dev/null)" ]; then

COUNT=1

for file in "$WORDLIST_DIR"/*.txt; do

if [ -f "$file" ]; then

FILENAME=$(basename "$file")

LINES=$(wc -l < "$file" 2>/dev/null || echo "0")

SIZE=$(du -h "$file" | cut -f1)

if [ "$file" == "$ACTIVE_WORDLIST" ]; then

echo -e "  ${GREEN}${BOLD}$COUNT. ★ $FILENAME${NC} ${CYAN}($LINES lines, $SIZE)${NC}"

else

echo -e "  ${WHITE}$COUNT.${NC} $FILENAME ${CYAN}($LINES lines, $SIZE)${NC}"

fi

((COUNT++))

fi

done

echo ""

echo -e "${WHITE}Pilih untuk set aktif (0=batal):${NC}"

read -p "> " SELECTION

if [ "$SELECTION" -gt 0 ] 2>/dev/null && [ "$SELECTION" -lt "$COUNT" ]; then

SELECTED_FILE=$(ls "$WORDLIST_DIR"/*.txt | sed -n "${SELECTION}p")

ACTIVE_WORDLIST="$SELECTED_FILE"

save_config

echo -e "${GREEN}✅ Aktif: $(basename $SELECTED_FILE)${NC}"

sleep 2

fi

else

echo -e "${YELLOW}Belum ada wordlist yang didownload${NC}"

fi

echo ""

read -p "Tekan Enter..."

}



# Delete wordlists

delete_wordlists() {

clear

print_header "Delete Wordlists"

echo ""

if [ -d "$WORDLIST_DIR" ] && [ "$(ls -A $WORDLIST_DIR/*.txt 2>/dev/null)" ]; then

COUNT=1

for file in "$WORDLIST_DIR"/*.txt; do

if [ -f "$file" ]; then

FILENAME=$(basename "$file")

SIZE=$(du -h "$file" | cut -f1)

echo -e "  ${WHITE}$COUNT.${NC} $FILENAME ${CYAN}($SIZE)${NC}"

((COUNT++))

fi

done

echo ""

echo -e "  ${RED}${BOLD}A. Delete All${NC}"

echo -e "  ${WHITE}0. Cancel${NC}"

echo ""

read -p "Pilih: " SELECTION

if [[ "$SELECTION" =~ ^[Aa]$ ]]; then

echo -e "${RED}${BOLD}Hapus SEMUA? (y/n):${NC}"

read -p "> " CONFIRM

if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then

rm -rf "$WORDLIST_DIR"/*.txt

ACTIVE_WORDLIST="embedded"

save_config

echo -e "${GREEN}✅ Semua dihapus${NC}"

sleep 2

fi

elif [ "$SELECTION" -gt 0 ] 2>/dev/null && [ "$SELECTION" -lt "$COUNT" ]; then

SELECTED_FILE=$(ls "$WORDLIST_DIR"/*.txt | sed -n "${SELECTION}p")

rm -f "$SELECTED_FILE"

if [ "$ACTIVE_WORDLIST" == "$SELECTED_FILE" ]; then

ACTIVE_WORDLIST="embedded"

save_config

fi

echo -e "${GREEN}✅ Dihapus${NC}"

sleep 2

fi

else

echo -e "${YELLOW}Tidak ada wordlist untuk dihapus${NC}"

sleep 2

fi

}



# Get wordlist

get_wordlist() {

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

generate_embedded_wordlist

elif [ -f "$ACTIVE_WORDLIST" ]; then

cat "$ACTIVE_WORDLIST"

else

ACTIVE_WORDLIST="embedded"

generate_embedded_wordlist

fi

}



# Handle Ctrl+C during scan

handle_scan_interrupt() {

SCAN_CANCELLED=true

echo ""

echo -e "${YELLOW}${BOLD}⚠️  Scan dibatalkan oleh user (Ctrl+C)${NC}"

}



# Full scan

full_cf_scan() {

if ! prompt_download_wordlist; then

return

fi

clear

print_header "Cloudflare Bug Scanner"

echo ""

echo -e "${WHITE}Masukkan target domain [default: ${CYAN}$DEFAULT_DOMAIN${WHITE}]:${NC}"

read -p "> " TARGET_DOMAIN

TARGET_DOMAIN=${TARGET_DOMAIN:-$DEFAULT_DOMAIN}

echo ""

echo -e "${BLUE}Domain:${NC} ${GREEN}$TARGET_DOMAIN${NC}"

echo -e "${BLUE}Target:${NC} ${GREEN}$TARGET_HOST${NC}"

echo -e "${BLUE}Filter:${NC} ${CYAN}☁️  Cloudflare only${NC}"

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

echo -e "${BLUE}Wordlist:${NC} ${YELLOW}Embedded (terbatas)${NC}"

else

echo -e "${BLUE}Wordlist:${NC} ${GREEN}$(basename $ACTIVE_WORDLIST)${NC}"

fi

echo ""

echo -e "${CYAN}📚 Loading wordlist...${NC}"

WORDLIST=$(get_wordlist)

TOTAL_WORDS=$(echo "$WORDLIST" | wc -l)

echo -e "${GREEN}✅ Loaded $TOTAL_WORDS patterns${NC}"

echo ""

echo -e "${CYAN}🧪 Scanning... Tekan ${WHITE}${BOLD}[Ctrl+C]${CYAN} untuk stop${NC}"

echo ""

WORK_BUGS=()

FAIL_BUGS=()

SKIPPED=0

CURRENT=0

START_TIME=$(date +%s)

SCAN_CANCELLED=false

trap 'handle_scan_interrupt' INT

while IFS= read -r SUB; do

[ -z "$SUB" ] && continue

if [ "$SCAN_CANCELLED" = true ]; then

break

fi

((CURRENT++))

PROGRESS=$((CURRENT * 100 / TOTAL_WORDS))

DOMAIN="$SUB.$TARGET_DOMAIN"

printf "\r${WHITE}[%3d%%]${NC} " "$PROGRESS"

IP=$(resolve_to_ip "$DOMAIN")

if [ -z "$IP" ]; then

printf "%-40s ${MAGENTA}⊘ No DNS${NC}" "$DOMAIN"

continue

fi

if ! is_cloudflare "$IP"; then

((SKIPPED++))

printf "%-40s ${YELLOW}⊗ Non-CF${NC}" "$DOMAIN"

continue

fi

printf "${CYAN}Testing:${NC} %-30s " "$DOMAIN"

if curl -s --max-time $TIMEOUT \

--resolve $TARGET_HOST:443:$IP \

https://$TARGET_HOST/ -o /dev/null 2>&1; then

printf "${GREEN}${BOLD}✅${NC}\n"

WORK_BUGS+=("$DOMAIN|$IP")

else

printf "${RED}❌${NC}\n"

FAIL_BUGS+=("$DOMAIN|$IP")

fi

sleep 0.05

done <<< "$WORDLIST"

trap - INT

END_TIME=$(date +%s)

ELAPSED=$((END_TIME - START_TIME))

echo ""

echo ""

print_header "HASIL SCAN"

echo ""

if [ "$SCAN_CANCELLED" = true ]; then

echo -e "${YELLOW}${BOLD}Catatan: Scan dibatalkan (hasil parsial)${NC}"

echo ""

fi

if [ ${#WORK_BUGS[@]} -gt 0 ]; then

echo -e "${GREEN}${BOLD}✅ Working Bugs (${#WORK_BUGS[@]}):${NC}"

for BUG in "${WORK_BUGS[@]}"; do

DOMAIN=$(echo $BUG | cut -d'|' -f1)

IP=$(echo $BUG | cut -d'|' -f2)

echo -e "  ${GREEN}🟢${NC} ${WHITE}$DOMAIN${NC} ${CYAN}($IP)${NC}"

done

echo ""

else

echo -e "${YELLOW}Tidak ada working bug ditemukan${NC}"

echo ""

fi

if [ ${#FAIL_BUGS[@]} -gt 0 ]; then

echo -e "${RED}${BOLD}❌ Failed Tests (${#FAIL_BUGS[@]}):${NC}"

for BUG in "${FAIL_BUGS[@]}"; do

DOMAIN=$(echo $BUG | cut -d'|' -f1)

IP=$(echo $BUG | cut -d'|' -f2)

echo -e "  ${RED}🔴${NC} ${WHITE}$DOMAIN${NC} ${CYAN}($IP)${NC}"

done

echo ""

fi

local width=$(tput cols 2>/dev/null || echo 60)

printf "${CYAN}"

printf '─%.0s' $(seq 1 $width)

printf "${NC}\n"

echo -e "${WHITE}${BOLD}Statistik:${NC}"

echo -e "  ${BLUE}Scanned:${NC} $CURRENT/$TOTAL_WORDS ${CYAN}(${PROGRESS}%)${NC}"

echo -e "  ${BLUE}CF Found:${NC} ${GREEN}$((${#WORK_BUGS[@]} + ${#FAIL_BUGS[@]}))${NC} | ${BLUE}Non-CF:${NC} ${YELLOW}$SKIPPED${NC}"

echo -e "  ${BLUE}Waktu:${NC} ${CYAN}${ELAPSED}s${NC}"

echo ""

if [ ${#WORK_BUGS[@]} -gt 0 ] || [ ${#FAIL_BUGS[@]} -gt 0 ]; then

echo -e "${WHITE}Export hasil? (y/n):${NC}"

read -p "> " EXPORT

if [[ "$EXPORT" =~ ^[Yy]$ ]]; then

OUTPUT="/sdcard/bug-$TARGET_DOMAIN-$(date +%Y%m%d-%H%M%S).txt"

{

echo "# InjectTools Scan Results"

echo "# Created by: t.me/hoshiyomi_id"

echo "# Domain: $TARGET_DOMAIN"

echo "# Date: $(date)"

echo "# Scan time: ${ELAPSED}s"

echo "# Patterns: $CURRENT/$TOTAL_WORDS"

[ "$SCAN_CANCELLED" = true ] && echo "# Status: Cancelled (partial)"

[ ${#WORK_BUGS[@]} -gt 0 ] && {

echo ""; echo "=== WORKING (${#WORK_BUGS[@]}) ===";

for B in "${WORK_BUGS[@]}"; do echo "✅ ${B/|/ }"; done

}

[ ${#FAIL_BUGS[@]} -gt 0 ] && {

echo ""; echo "=== FAILED (${#FAIL_BUGS[@]}) ===";

for B in "${FAIL_BUGS[@]}"; do echo "❌ ${B/|/ }"; done

}

} > "$OUTPUT"

echo -e "${GREEN}${BOLD}✅ Tersimpan: $OUTPUT${NC}"

sleep 2

fi

fi

echo ""

read -p "Tekan Enter untuk kembali ke menu..."

}



# Settings

settings_menu() {

clear

print_header "SETTINGS"

echo ""

echo -e "${WHITE}${BOLD}Konfigurasi Saat Ini:${NC}"

echo -e "  ${BLUE}Target Host:${NC} ${GREEN}$TARGET_HOST${NC}"

echo -e "  ${BLUE}Default Domain:${NC} ${GREEN}$DEFAULT_DOMAIN${NC}"

echo -e "  ${BLUE}Default Subdomain:${NC} ${GREEN}$DEFAULT_SUBDOMAIN${NC}"

echo -e "  ${BLUE}Timeout:${NC} ${CYAN}${TIMEOUT}s${NC}"

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

echo -e "  ${BLUE}Wordlist:${NC} ${YELLOW}Embedded${NC}"

else

echo -e "  ${BLUE}Wordlist:${NC} ${GREEN}$(basename $ACTIVE_WORDLIST)${NC}"

fi

echo ""

echo -e "  ${WHITE}1.${NC} Change Target Host"

echo -e "  ${WHITE}2.${NC} Change Default Domain"

echo -e "  ${WHITE}3.${NC} Change Default Subdomain"

echo -e "  ${WHITE}4.${NC} Change Timeout"

echo -e "  ${WHITE}5.${NC} Reset to Embedded Wordlist"

echo -e "  ${WHITE}6.${NC} Re-run First Time Setup"

echo -e "  ${WHITE}7.${NC} Back"

echo ""

read -p "Pilih [1-7]: " opt

case $opt in

1)

echo -e "${WHITE}Target host baru:${NC}"

read -p "> " NEW_HOST

if [ -n "$NEW_HOST" ]; then

TARGET_HOST=$NEW_HOST

save_config

fi

sleep 1

settings_menu

;;

2)

echo -e "${WHITE}Default domain baru:${NC}"

read -p "> " NEW_DOMAIN

if [ -n "$NEW_DOMAIN" ]; then

DEFAULT_DOMAIN=$NEW_DOMAIN

save_config

fi

sleep 1

settings_menu

;;

3)

echo -e "${WHITE}Default subdomain baru:${NC}"

read -p "> " NEW_SUBDOMAIN

if [ -n "$NEW_SUBDOMAIN" ]; then

DEFAULT_SUBDOMAIN=$NEW_SUBDOMAIN

DEFAULT_DOMAIN=$(extract_domain "$DEFAULT_SUBDOMAIN")

save_config

echo -e "${BLUE}📌 Domain auto-updated: ${GREEN}$DEFAULT_DOMAIN${NC}"

fi

sleep 2

settings_menu

;;

4)

echo -e "${WHITE}Timeout (detik):${NC}"

read -p "> " NEW_TIMEOUT

if [[ "$NEW_TIMEOUT" =~ ^[0-9]+$ ]]; then

TIMEOUT=$NEW_TIMEOUT

save_config

fi

sleep 1

settings_menu

;;

5)

ACTIVE_WORDLIST="embedded"

save_config

echo -e "${GREEN}✅ Reset ke embedded${NC}"

sleep 1

settings_menu

;;

6)

first_time_setup

;;

7) return ;;

esac

}



# Main menu

main_menu() {

while true; do

clear

print_header "InjectTools v1.1"

echo ""

echo -e "${CYAN}Created by: t.me/hoshiyomi_id${NC}"

echo ""

echo -e "${BLUE}Target:${NC} ${GREEN}$TARGET_HOST${NC}"

if [ "$ACTIVE_WORDLIST" == "embedded" ]; then

echo -e "${BLUE}Wordlist:${NC} ${YELLOW}Embedded (terbatas)${NC}"

else

WNAME=$(basename "$ACTIVE_WORDLIST")

WLINES=$(wc -l < "$ACTIVE_WORDLIST" 2>/dev/null || echo "0")

echo -e "${BLUE}Wordlist:${NC} ${GREEN}$WNAME${NC} ${CYAN}($WLINES lines)${NC}"

fi

echo ""

local width=$(tput cols 2>/dev/null || echo 60)

printf "${CYAN}"

printf '─%.0s' $(seq 1 $width)

printf "${NC}\n"

echo ""

echo -e "${WHITE}${BOLD}Menu:${NC}"

echo ""

echo -e "  ${WHITE}1.${NC} Test Single Subdomain"

echo -e "  ${WHITE}2.${NC} Full Cloudflare Scan"

echo -e "  ${WHITE}3.${NC} Download Wordlist (from SecLists)"

echo -e "  ${WHITE}4.${NC} Settings"

echo -e "  ${WHITE}5.${NC} Exit"

echo ""

read -p "Pilih [1-5]: " choice

case $choice in

1) test_single_subdomain ;;

2) full_cf_scan ;;

3) download_wordlist_menu ;;

4) settings_menu ;;

5)

clear

echo -e "${GREEN}${BOLD}Sampai jumpa!${NC}"

echo -e "${CYAN}Created by: t.me/hoshiyomi_id${NC}"

echo ""

exit 0

;;

*) echo -e "${RED}${BOLD}Pilihan tidak valid!${NC}"; sleep 1 ;;

esac

done

}



# Main execution

check_dependencies

if ! load_config; then

auto_detect_wordlist

first_time_setup

else

auto_detect_wordlist

fi

main_menu