# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Mavenを使う。`pom.xml` はプロジェクトルートにある。

```bash
# コンパイル
mvn compile

# サーバー起動（ポート省略時は 8080）
mvn exec:java -Dexec.mainClass=apps.Server
mvn exec:java -Dexec.mainClass=apps.Server -Dexec.args=9090

# クライアント起動（別ターミナルで）
mvn exec:java -Dexec.mainClass=apps.Client
mvn exec:java -Dexec.mainClass=apps.Client -Dexec.args=9090

# テスト実行
mvn test
```

`target/` と `.class` ファイルは `.gitignore` で除外済みのためコミットしない。

## アーキテクチャ

**現状**: 1対1のTCP echoサーバー。`Server.java` と `Client.java` のみ。

**目標**: リアルタイム早押しクイズ。複数クライアントがサーバーに接続し、サーバーが問題出題・早押し判定・得点管理を行う。

設計の詳細は [docs/design/2026-05-03-communication-sync.md](docs/design/2026-05-03-communication-sync.md) を参照。

### 通信プロトコル

TCPバイトストリーム上に独自フレーミングを実装する予定:

```
| type (1 byte) | body_length (4 bytes, big-endian) | body (n bytes) |
```

現状の `readLine()` による改行区切りはプロトタイプのみ。

### スレッドモデル

クライアント1人につき1スレッドを立て、受信メッセージを `BlockingQueue` に積む。ゲームロジックスレッドがキューからデキューして処理する。

## 役割分担

| 担当 | 責務 |
|---|---|
| A | サーバー側ゲームロジック（問題出題・得点管理） |
| B | フレーミング・マルチクライアント接続・早押し同期制御 |
| C | クライアントCUI・テスト・デモ確認 |
