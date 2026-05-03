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

# ---------- git ----------
echo "--- git ---"
if command -v git &>/dev/null; then
    ok "git $(git --version | awk '{print $3}')"
else
    fail "git が見つかりません"
    echo "  → https://git-scm.com/downloads からインストールしてください"
    exit 1
fi

# ---------- Java ----------
echo ""
echo "--- Java ---"
if command -v javac &>/dev/null; then
    JAVA_VER=$(javac -version 2>&1 | awk '{print $2}')
    JAVA_MAJOR=$(echo "$JAVA_VER" | cut -d. -f1)
    if [ "$JAVA_MAJOR" -ge 11 ]; then
        ok "javac $JAVA_VER"
    else
        warn "javac $JAVA_VER (Java 11 以上を推奨します)"
    fi
else
    fail "javac が見つかりません"
    echo "  → JDK をインストールしてください"
    echo "     macOS:   brew install openjdk"
    echo "     Ubuntu:  sudo apt install default-jdk"
    echo "     Windows: https://adoptium.net/"
    exit 1
fi

# ---------- コンパイル ----------
echo ""
echo "--- コンパイル ---"
cd "$(dirname "$0")"

if javac Server.java Client.java 2>/dev/null; then
    ok "Server.java, Client.java のコンパイル成功"
else
    fail "コンパイルに失敗しました"
    echo "  → 以下のコマンドでエラー内容を確認してください:"
    echo "     javac Server.java Client.java"
    exit 1
fi

# ---------- 起動確認 ----------
echo ""
echo "--- 起動確認 ---"

# サーバーをバックグラウンドで立てて接続できるか確認する
java Server 18080 &
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
echo "  ターミナル1: java Server"
echo "  ターミナル2: java Client"
echo ""
echo "詳細は CLAUDE.md を確認してください。"
