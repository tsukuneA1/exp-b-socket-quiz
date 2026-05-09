#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[NG]${NC} $1"; }

echo "=== exp-b-socket-quiz セットアップ確認 ==="
echo ""

# ---------- Java ----------
echo "--- Java ---"
if command -v javac &>/dev/null; then
    JAVA_VER=$(javac -version 2>&1 | awk '{print $2}')
    JAVA_MAJOR=$(echo "$JAVA_VER" | cut -d. -f1)
    if [ "$JAVA_MAJOR" -ge 17 ]; then
        ok "javac $JAVA_VER"
    else
        warn "javac $JAVA_VER (Java 17 以上が必要です)"
        exit 1
    fi
else
    fail "javac が見つかりません"
    echo "  → JDK をインストールしてください"
    echo "     macOS:   brew install openjdk@17"
    echo "     Ubuntu:  sudo apt install openjdk-17-jdk"
    echo "     Windows: https://adoptium.net/"
    exit 1
fi

# ---------- Maven ----------
echo ""
echo "--- Maven ---"
if command -v mvn &>/dev/null; then
    MVN_VER=$(mvn -version 2>&1 | head -1)
    ok "$MVN_VER"
else
    fail "mvn が見つかりません"
    echo "  → Maven をインストールしてください"
    echo "     macOS:   brew install maven"
    echo "     Ubuntu:  sudo apt install maven"
    echo "     Windows: https://maven.apache.org/download.cgi"
    exit 1
fi

# ---------- コンパイル ----------
echo ""
echo "--- コンパイル ---"
cd "$(dirname "$0")"

if mvn compile -q 2>/dev/null; then
    ok "mvn compile 成功"
else
    fail "コンパイルに失敗しました"
    echo "  → 以下のコマンドでエラー内容を確認してください:"
    echo "     mvn compile"
    exit 1
fi

# ---------- 起動確認 ----------
echo ""
echo "--- 起動確認 ---"

mvn exec:java -Dexec.mainClass=apps.Server -Dexec.args=18080 -q &
SERVER_PID=$!
sleep 1

if kill -0 $SERVER_PID 2>/dev/null; then
    ok "Server 起動確認 (port 18080)"
    kill $SERVER_PID 2>/dev/null
    wait $SERVER_PID 2>/dev/null || true
else
    fail "Server の起動に失敗しました"
    exit 1
fi

# ---------- 完了 ----------
echo ""
echo "=================================="
ok "セットアップ完了！"
echo ""
echo "起動方法:"
echo "  ターミナル1: mvn exec:java -Dexec.mainClass=apps.Server"
echo "  ターミナル2: mvn exec:java -Dexec.mainClass=apps.Client"
echo ""
echo "詳細は CLAUDE.md を確認してください。"
